package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CREATE, UPDATE, or DELETE */
    @Column(nullable = false, length = 20)
    private String action;

    /** The PaymentEntry this log refers to */
    private Long entryId;

    @Column(nullable = false)
    private String employeeName;

    @Column(nullable = false)
    private String employeeUsername;

    @Column(nullable = false)
    private String partyName;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 50)
    private String modeOfPayment;

    private LocalDate entryDate;

    @Column(length = 500)
    private String remarks;

    /** Username of whoever performed the action */
    @Column(nullable = false)
    private String performedBy;

    /** Human-readable diff for UPDATE; "Created" / "Deleted" for others */
    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        performedAt = LocalDateTime.now();
    }
}
