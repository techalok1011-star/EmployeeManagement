package com.empmgmt.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyOutstandingDTO {

    private Long partyId;       // Party.id — null if party not in parties table
    private String partyName;   // combined key (name + '_' + gstin, or just name)
    private String displayName; // human-readable portion before the first '_'
    private String gstin;       // may be null / empty
    private String trailingNumber; // trailing ledger code from source accounting system, may be null

    private BigDecimal totalInvoiced;  // sum of all invoice amounts for this party
    private BigDecimal totalPaid;      // sum of all payment-entry amounts for this party
    private BigDecimal outstanding;    // totalInvoiced - totalPaid  (negative = credit)

    private String phone;          // E.164 WhatsApp number, may be null
    private boolean whatsappOptIn; // true = opted in to reminders

    /** True when outstanding ≤ 0 (party has paid more than invoiced) */
    public boolean isCredit() {
        return outstanding != null && outstanding.compareTo(BigDecimal.ZERO) <= 0;
    }
}
