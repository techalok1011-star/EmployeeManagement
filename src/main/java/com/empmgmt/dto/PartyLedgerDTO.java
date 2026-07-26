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
        private BigDecimal balance; // running balance after this entry (closing balance, for FY_SUMMARY rows)
        private LocalDateTime sortTiebreak; // createdAt — stable ordering for same-day entries

        // FY_SUMMARY rows only — a synthetic row marking a fiscal-year boundary.
        // Deliberately kept separate from debit/credit (rather than reusing them)
        // so generic "sum all entries' debit/credit" calculations elsewhere
        // (e.g. the PDF export's period-total line) don't double-count a year's
        // transactions once plus again via its own summary row.
        private String fyLabel;         // e.g. "FY 2025-26"
        private BigDecimal fyTotalDebit;
        private BigDecimal fyTotalCredit;
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
        private int transactionCount; // real invoice/payment rows, excluding synthetic FY_SUMMARY rows
    }
}
