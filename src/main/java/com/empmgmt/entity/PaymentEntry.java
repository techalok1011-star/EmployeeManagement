package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String partyName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModeOfPayment modeOfPayment;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(length = 500)
    private String remarks;

    /** Voucher number from the source Receipt Register (e.g. Tally), if this payment was imported. */
    @Column(name = "receipt_vch_no", length = 20)
    private String receiptVchNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Whether this entry has been edited after creation */
    @Column(nullable = false)
    private boolean edited = false;

    /**
     * Who last edited: "EMPLOYEE" | "ADMIN" | null (not edited)
     */
    @Column(length = 20)
    private String editedBy;

    private LocalDateTime editedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ModeOfPayment {
        CASH("Cash"),
        CHEQUE("Cheque"),
        BANK_TRANSFER("Bank Transfer"),
        UPI("UPI"),
        NEFT("NEFT"),
        RTGS("RTGS"),
        DD("Demand Draft");

        private final String displayName;

        ModeOfPayment(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
