package com.empmgmt.service;

import com.empmgmt.dto.UserDTO;
import com.empmgmt.entity.User;
import com.empmgmt.repository.PaymentEntryRepository;
import com.empmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PaymentEntryRepository paymentEntryRepository;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public UserDTO.Response createEmployee(UserDTO.CreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        User.Role role = request.getRole() != null ? request.getRole() : User.Role.EMPLOYEE;
        if (role == User.Role.ADMIN) {
            throw new RuntimeException("Admin accounts cannot be created through this form.");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .role(role)
                .active(true)
                .build();

        user = userRepository.save(user);
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserDTO.Response> getAllEmployees() {
        return userRepository.findByRole(User.Role.EMPLOYEE)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDTO.Response> getAllAccountants() {
        return userRepository.findByRole(User.Role.ACCOUNTANT)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDTO.Response> getAllManagers() {
        return userRepository.findByRole(User.Role.MANAGER)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDTO.Response getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserDTO.Response getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToResponse(user);
    }

    public UserDTO.Response updateEmployee(Long id, UserDTO.UpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setActive(request.isActive());

        user = userRepository.save(user);
        return mapToResponse(user);
    }

    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void toggleStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(!user.isActive());
        userRepository.save(user);
    }

    private UserDTO.Response mapToResponse(User user) {
        // Get today's stats
        BigDecimal totalAmount = paymentEntryRepository
                .sumAmountByEmployeeAndDate(user, LocalDate.now());
        long totalEntries = paymentEntryRepository
                .countByEmployeeAndDate(user, LocalDate.now());

        return UserDTO.Response.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(FORMATTER) : "")
                .totalEntries(totalEntries)
                .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
                .build();
    }
}
