package com.empmgmt.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PartyLedgerDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Entry {
        private LocalDate date;
        private String type;        // "INVOICE" | "PAYMENT"
        private String reference;   // invoice number, or payment mode display name
        private String description; // invoice description, or remarks
        private String deliveryInfo; // "Truck • MH12AB1234" — invoice rows only, null for payments
        private String salesVchNo;   // set for INVOICE rows imported from a Sales Register, else null
        private String receiptVchNo; // set for PAYMENT rows imported from a Receipt Register, else null
        private BigDecimal debit;   // set for INVOICE rows
        private BigDecimal credit;  // set for PAYMENT rows
        private BigDecimal balance; // running balance after this entry
        private LocalDateTime sortTiebreak; // createdAt — stable ordering for same-day entries
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long partyId;
        private String partyName;   // combined key
        private String displayName;
        private String gstin;
        private String trailingNumber;
        private BigDecimal totalInvoiced;
        private BigDecimal totalPaid;
        private BigDecimal outstanding;
        private List<Entry> entries; // newest first
    }
}
