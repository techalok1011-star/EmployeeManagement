package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Generic audit trail for actions that aren't a PaymentEntry (those already have
 * their own trail in {@link TransactionLog}, unchanged). Deliberately plain Strings
 * for action/entityType, not @Enumerated - this table logs many different action
 * verbs and is expected to grow; a CHECK-constrained enum column would need a
 * manual ALTER TABLE every time a new action type is added (see the project's
 * documented enum/CHECK-constraint gotcha).
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(columnList = "performed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "CREATE" | "UPDATE" | "DELETE" - same vocabulary as TransactionLog.action, so the
     *  existing Audit Log page's filter buttons/badges work across both sources unchanged. */
    @Column(nullable = false, length = 20)
    private String action;

    /** "INVOICE" | "PARTY" */
    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "party_name", length = 700)
    private String partyName;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @Column(name = "performed_by", length = 50)
    private String performedBy;

    @Column(name = "performed_by_role", length = 20)
    private String performedByRole;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        performedAt = LocalDateTime.now();
    }
}
