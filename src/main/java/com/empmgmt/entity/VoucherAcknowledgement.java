package com.empmgmt.entity;

import com.empmgmt.util.IstClock;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A human explanation for a range of voucher numbers that never got written - a voided page, a
 * spoiled receipt, a page used for a transfer, or anything else worth recording. Doesn't touch
 * invoices/payment_entries at all; VoucherAuditService cross-references this table at read time
 * to keep an explained gap out of the "active" missing count while leaving it on record, rather
 * than the same unexplained-looking number showing up on every visit.
 * <p>
 * {@code series} and {@code reasonCategory} are plain Strings, not {@code @Enumerated} - this
 * table is exactly the kind that grows a new reason category over time, and a Postgres CHECK
 * constraint on an enum column needs a manual ALTER TABLE for every addition (see the audit_logs
 * table for the same deliberate choice).
 */
@Entity
@Table(name = "voucher_acknowledgements", indexes = {
        @Index(columnList = "series, financial_year")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherAcknowledgement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RECEIPT or SALES. */
    @Column(nullable = false, length = 20)
    private String series;

    /** e.g. "2026-27". */
    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "range_start", nullable = false)
    private long rangeStart;

    @Column(name = "range_end", nullable = false)
    private long rangeEnd;

    /** VOIDED, SPOILED, TRANSFER, or EXPLAINED. */
    @Column(name = "reason_category", nullable = false, length = 20)
    private String reasonCategory;

    @Column(length = 500)
    private String note;

    @Column(name = "acknowledged_by", length = 50)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at", updatable = false)
    private LocalDateTime acknowledgedAt;

    @PrePersist
    protected void onCreate() {
        acknowledgedAt = IstClock.now();
    }
}
