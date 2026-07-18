package com.empmgmt.entity;

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
        createdAt = LocalDateTime.now();
    }

    public enum NotificationType {
        COLLECTION_ADDED,
        INVOICE_ADDED
    }
}
