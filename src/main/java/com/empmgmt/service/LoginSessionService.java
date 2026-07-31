package com.empmgmt.service;

import com.empmgmt.entity.LoginSession;
import com.empmgmt.entity.User;
import com.empmgmt.repository.LoginSessionRepository;
import com.empmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tracks login/logout timing for the admin "Login History" report - see SecurityConfig's successHandler/logoutSuccessHandler. */
@Service
@RequiredArgsConstructor
@Transactional
public class LoginSessionService {

    private final LoginSessionRepository loginSessionRepository;
    private final UserRepository userRepository;

    public void recordLogin(String username, BigDecimal latitude, BigDecimal longitude) {
        User user = userRepository.findByUsername(username).orElse(null);
        loginSessionRepository.save(LoginSession.builder()
                .username(username)
                .fullName(user != null ? user.getFullName() : username)
                .role(user != null ? user.getRole().name() : null)
                .loginAt(LocalDateTime.now())
                .latitude(latitude)
                .longitude(longitude)
                .build());
    }

    public void recordLogout(String username) {
        if (username == null) return;
        loginSessionRepository.findTopByUsernameAndLogoutAtIsNullOrderByLoginAtDesc(username)
                .ifPresent(session -> {
                    session.setLogoutAt(LocalDateTime.now());
                    loginSessionRepository.save(session);
                });
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentSessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LoginSession s : loginSessionRepository.findTop200ByOrderByLoginAtDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", s.getUsername());
            row.put("fullName", s.getFullName());
            row.put("role", s.getRole());
            row.put("loginAt", s.getLoginAt());
            row.put("logoutAt", s.getLogoutAt());
            row.put("latitude", s.getLatitude());
            row.put("longitude", s.getLongitude());
            row.put("active", s.getLogoutAt() == null);
            LocalDateTime end = s.getLogoutAt() != null ? s.getLogoutAt() : LocalDateTime.now();
            row.put("durationText", formatDuration(Duration.between(s.getLoginAt(), end)));
            result.add(row);
        }
        return result;
    }

    private String formatDuration(Duration d) {
        long totalMinutes = Math.max(0, d.toMinutes());
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
