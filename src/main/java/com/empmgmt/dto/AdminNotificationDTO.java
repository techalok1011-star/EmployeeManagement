package com.empmgmt.dto;

import lombok.*;
import java.math.BigDecimal;

public class AdminNotificationDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String type;            // COLLECTION_ADDED | INVOICE_ADDED
        private String message;
        private String partyName;
        private BigDecimal amount;
        private String triggeredBy;
        private String triggeredByRole;
        private String sourceType;
        private Long sourceId;
        private boolean read;
        private String createdAt;
    }
}
