package com.empmgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

public class PartyMergeDTO {

    /** One row in the orphaned-party worklist - a party_name with no matching parties.combined row. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrphanRow {
        private String partyName;
        private long invoiceCount;
        private BigDecimal invoiceAmount;
        private long paymentCount;
        private BigDecimal paymentAmount;
        private BigDecimal totalAmount;
    }

    /** Live side-by-side preview shown before a merge is confirmed. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Preview {
        private String fromName;
        private boolean fromHasPartyRecord;
        private long fromInvoiceCount;
        private BigDecimal fromInvoiceAmount;
        private long fromPaymentCount;
        private BigDecimal fromPaymentAmount;

        private String toCombined;
        private boolean toExists;
        private String toPhone;
        private boolean toWhatsappOptIn;
        private long toInvoiceCount;
        private BigDecimal toInvoiceAmount;
        private long toPaymentCount;
        private BigDecimal toPaymentAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "Choose the party name to merge from")
        private String fromName;

        @NotBlank(message = "Choose the existing party to merge into")
        private String toCombined;

        @NotBlank(message = "A reason for the merge is required")
        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        private String remarks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Result {
        private String fromName;
        private String toCombined;
        private long invoicesMoved;
        private BigDecimal invoiceAmount;
        private long paymentsMoved;
        private BigDecimal paymentAmount;
    }
}
