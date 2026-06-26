package com.empmgmt.dto;

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

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "Invalid amount format")
        private BigDecimal amount;

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        private String description;
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
        private String description;
        private String createdAt;
    }
}
