package com.empmgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

public class PartyDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {

        @NotBlank(message = "Party name is required")
        @Size(max = 500, message = "Name must not exceed 500 characters")
        private String name;

        @Size(max = 128, message = "GSTIN must not exceed 128 characters")
        private String gstin;   // optional

        @Size(max = 20, message = "Trailing number must not exceed 20 characters")
        private String trailingNumber;  // ledger code from source accounting system, optional

        @Size(max = 20, message = "Phone must not exceed 20 characters")
        private String phone;   // E.164, optional

        private boolean whatsappOptIn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String gstin;
        private String combined;
        private BigDecimal totalAmount;
        private String phone;
        private boolean whatsappOptIn;
    }
}
