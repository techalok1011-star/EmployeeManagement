package com.empmgmt.config;

import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.entity.User;
import com.empmgmt.repository.PaymentEntryRepository;
import com.empmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PaymentEntryRepository paymentEntryRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("=== DATA ALREADY EXISTS, SKIPPING SEED ===");
            return;
        }
        // Create Admin
        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .fullName("System Administrator")
                .email("admin@company.com")
                .role(User.Role.ADMIN)
                .active(true)
                .build();
        userRepository.save(admin);

        // Create Employees
        User emp1 = User.builder()
                .username("rahul.sharma")
                .password(passwordEncoder.encode("emp123"))
                .fullName("Rahul Sharma")
                .email("rahul@company.com")
                .role(User.Role.EMPLOYEE)
                .active(true)
                .build();
        userRepository.save(emp1);

        User emp2 = User.builder()
                .username("priya.patel")
                .password(passwordEncoder.encode("emp123"))
                .fullName("Priya Patel")
                .email("priya@company.com")
                .role(User.Role.EMPLOYEE)
                .active(true)
                .build();
        userRepository.save(emp2);

        User emp3 = User.builder()
                .username("amit.kumar")
                .password(passwordEncoder.encode("emp123"))
                .fullName("Amit Kumar")
                .email("amit@company.com")
                .role(User.Role.EMPLOYEE)
                .active(true)
                .build();
        userRepository.save(emp3);

        // Seed some sample entries
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        paymentEntryRepository.save(PaymentEntry.builder()
                .partyName("Tata Industries Ltd").amount(new BigDecimal("150000.00"))
                .modeOfPayment(PaymentEntry.ModeOfPayment.NEFT)
                .entryDate(today).employee(emp1).remarks("Q3 payment").build());

        paymentEntryRepository.save(PaymentEntry.builder()
                .partyName("Reliance Corp").amount(new BigDecimal("75500.50"))
                .modeOfPayment(PaymentEntry.ModeOfPayment.CHEQUE)
                .entryDate(today).employee(emp1).remarks("Invoice #4521").build());

        paymentEntryRepository.save(PaymentEntry.builder()
                .partyName("Infosys Solutions").amount(new BigDecimal("25000.00"))
                .modeOfPayment(PaymentEntry.ModeOfPayment.UPI)
                .entryDate(today).employee(emp2).build());

        paymentEntryRepository.save(PaymentEntry.builder()
                .partyName("Wipro Technologies").amount(new BigDecimal("320000.00"))
                .modeOfPayment(PaymentEntry.ModeOfPayment.RTGS)
                .entryDate(yesterday).employee(emp2).remarks("Annual contract").build());

        paymentEntryRepository.save(PaymentEntry.builder()
                .partyName("HCL Ltd").amount(new BigDecimal("12000.00"))
                .modeOfPayment(PaymentEntry.ModeOfPayment.CASH)
                .entryDate(today).employee(emp3).build());

        log.info("=== DATA SEEDED SUCCESSFULLY ===");
        log.info("Admin  - username: admin     | password: admin123");
        log.info("Emp 1  - username: rahul.sharma | password: emp123");
        log.info("Emp 2  - username: priya.patel  | password: emp123");
        log.info("Emp 3  - username: amit.kumar   | password: emp123");
    }
}
