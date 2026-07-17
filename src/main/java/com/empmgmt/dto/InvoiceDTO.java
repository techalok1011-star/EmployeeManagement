package com.empmgmt.dto;

import com.empmgmt.entity.Invoice;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InvoiceDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {

        @NotBlank(message = "Invoice number is required")
        @Size(max = 100, message = "Invoice number must not exceed 100 characters")
        private String invoiceNumber;

        @NotNull(message = "Invoice date is required")
        private LocalDate invoiceDate;

        @NotBlank(message = "Party name is required")
        @Size(max = 700, message = "Party name must not exceed 700 characters")
        private String partyName;

        /** Derived server-side as bags * ratePerBag - not bound to a form field, not validated directly. */
        private BigDecimal amount;

        @NotNull(message = "Number of bags is required")
        @Min(value = 1, message = "Bags must be at least 1")
        private Integer bags;

        @NotNull(message = "Rate per bag is required")
        @DecimalMin(value = "0.01", message = "Rate must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Invalid rate format")
        private BigDecimal ratePerBag;

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        private String description;

        @NotNull(message = "Delivery mode is required")
        private Invoice.DeliveryMode deliveryMode;

        @Size(max = 50, message = "Transport number must not exceed 50 characters")
        private String transportNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String invoiceNumber;
        private LocalDate invoiceDate;
        private String partyName;
        private BigDecimal amount;
        private Integer bags;
        private BigDecimal ratePerBag;
        private String description;
        private String deliveryMode;
        private String transportNumber;
        private String salesVchNo;
        private String createdBy;
        private String createdAt;
    }
}
