package com.empmgmt.dto;

import com.empmgmt.entity.PaymentEntry;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PaymentEntryDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "Party name is required")
        @Size(max = 200, message = "Party name must not exceed 200 characters")
        private String partyName;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "Invalid amount format")
        private BigDecimal amount;

        @NotNull(message = "Mode of payment is required")
        private PaymentEntry.ModeOfPayment modeOfPayment;

        @NotNull(message = "Date is required")
        private LocalDate entryDate;

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        private String remarks;

        @Size(max = 20, message = "Receipt Vch No. must not exceed 20 characters")
        private String receiptVchNo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String partyName;
        private BigDecimal amount;
        private String modeOfPayment;
        private LocalDate entryDate;
        private String remarks;
        private String receiptVchNo;
        private String employeeName;
        private String employeeUsername;
        private String createdAt;
        private String updatedAt;
        // Edit tracking
        private boolean edited;
        private String editedBy;   // "EMPLOYEE" | "ADMIN" | null
        private String editedAt;
        /** Set via a batched lookup by the admin entries listing, not populated by mapToResponse() itself. */
        private boolean hasReceipt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailySummary {
        private LocalDate date;
        private long totalEntries;
        private BigDecimal totalAmount;
        private String employeeName;
    }

    /** Groups entries belonging to a single calendar day */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayGroup {
        private LocalDate date;
        private String dateFormatted;   // e.g. "Wed, 07 May 2025"
        private long totalEntries;
        private BigDecimal totalAmount;
        private List<Response> entries;
    }
}
