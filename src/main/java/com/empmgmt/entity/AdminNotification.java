package com.empmgmt.entity;

import com.empmgmt.util.IstClock;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_notifications", indexes = {
        @Index(columnList = "is_read"),
        @Index(columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "party_name", length = 700)
    private String partyName;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    /** Username of whoever performed the action that triggered this notification. */
    @Column(name = "triggered_by", length = 50)
    private String triggeredBy;

    @Column(name = "triggered_by_role", length = 20)
    private String triggeredByRole;

    /** "PAYMENT_ENTRY" | "INVOICE" */
    @Column(name = "source_type", length = 20)
    private String sourceType;

    /** Id of the PaymentEntry or Invoice row this notification is about. */
    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        createdAt = IstClock.now();
    }

    public enum NotificationType {
        COLLECTION_ADDED,
        INVOICE_ADDED,
        /** A newly-saved voucher number matches one already used this financial year. */
        VOUCHER_DUPLICATE,
        /** A newly-saved voucher number is far outside this year's normal range - likely a typo. */
        VOUCHER_SUSPECT,
        /** A new party was created by someone other than the admin. */
        PARTY_ADDED,
        /** Daily 10 PM summary of receipt voucher numbers still missing after the grace period. */
        VOUCHER_GAP_DIGEST,
        /** Server errors (5xx) spiked - raised by monitoring.ErrorSpikeAlert. */
        SERVER_ALERT
    }
}
