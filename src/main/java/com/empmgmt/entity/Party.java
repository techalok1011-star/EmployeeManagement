package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "parties", indexes = {@Index(columnList = "combined")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 500)
    private String name;

    @Column(name = "gst", length = 128)
    private String gst;

    @Column(name = "combined", length = 700, unique = true)
    private String combined; // name + '_' + gst or name

    /** Trailing numeric ledger code from the source accounting system (e.g. Tally), if any. */
    @Column(name = "trailing_number", length = 20)
    private String trailingNumber;

    /** Total sales amount from Excel (sum across all invoices for this party) */
    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    /** WhatsApp phone number in E.164 format, e.g. 919876543210 */
    @Column(name = "phone", length = 20)
    private String phone;

    /** Whether this party has opted in to receive WhatsApp reminders */
    @Column(name = "whatsapp_opt_in", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean whatsappOptIn = false;

    /**
     * Balance carried forward from before this system's transaction history began -
     * every real ledger has one. Positive = Dr (party owes us), negative = Cr (we owe
     * them / advance held), matching the sign convention already used for {@code outstanding}
     * everywhere else in the app. Defaults to zero (DB column is NOT NULL DEFAULT 0), so no
     * null-checks needed anywhere this is read. Previously represented, inconsistently and
     * per-party, as a synthetic "TALLY-S-...-OPEN-<party>" invoice row or an "Opening balance
     * (advance)..." payment row - this column replaces that convention going forward; those
     * existing synthetic rows have NOT yet been migrated/removed as of when this was added,
     * so don't assume every party's opening balance is captured here yet.
     */
    @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /** The date opening_balance is carried forward as of - needed to place it correctly in a chronological ledger. */
    @Column(name = "opening_balance_date")
    private LocalDate openingBalanceDate;
}

