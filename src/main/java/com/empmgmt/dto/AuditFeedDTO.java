package com.empmgmt.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AuditFeedDTO {

    /**
     * One row in the unified Audit Log feed - merges {@link com.empmgmt.entity.TransactionLog}
     * (payment entries) and {@link com.empmgmt.entity.AuditLog} (invoices, parties) into a
     * single chronological view. Fields that don't apply to a given category are left
     * null/blank rather than recomputed - e.g. modeOfPayment only means something for
     * "Payment Entry" rows.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String category;    // "Payment Entry" | "Invoice" | "Party"
        private String action;      // CREATE | UPDATE | DELETE
        private String employeeName;
        private String employeeUsername;
        private String partyName;
        private BigDecimal amount;
        private String modeOfPayment;
        private LocalDate entryDate;
        private String performedBy;
        private String performedByRole; // null for legacy payment-entry rows - role wasn't captured historically
        private String notes;
        private String performedAtDisplay;
        private LocalDateTime performedAtRaw; // sort key only, not for display
    }
}
