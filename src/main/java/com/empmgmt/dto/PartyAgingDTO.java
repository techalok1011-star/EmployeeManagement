package com.empmgmt.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyAgingDTO {

    private Long partyId;       // null if party not in parties table
    private String partyName;   // combined key
    private String displayName;
    private String gstin;

    private BigDecimal outstanding;

    /** Invoice date of the oldest invoice not yet covered by payments (FIFO allocation). Null if it can't be determined. */
    private LocalDate oldestUnpaidInvoiceDate;
    private long daysOverdue;

    /** "0-30" | "31-60" | "61-90" | "90+" */
    private String bucket;

    private String phone;
    private boolean whatsappOptIn;
}
