package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs", indexes = {
        @Index(columnList = "party_name"),
        @Index(columnList = "sent_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_name", length = 700)
    private String partyName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "outstanding_amount", precision = 15, scale = 2)
    private BigDecimal outstandingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** "SCHEDULER" | "ADMIN:<username>" */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public enum Status {
        /** Message successfully sent via Meta API */
        SENT,
        /** Meta API call failed */
        FAILED,
        /** Mode = LOG_ONLY — would have been sent in LIVE mode */
        DRY_RUN
    }
}
