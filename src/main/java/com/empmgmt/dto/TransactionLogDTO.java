package com.empmgmt.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionLogDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String action;          // CREATE | UPDATE | DELETE
        private Long entryId;
        private String employeeName;
        private String employeeUsername;
        private String partyName;
        private BigDecimal amount;
        private String modeOfPayment;
        private LocalDate entryDate;
        private String remarks;
        private String performedBy;
        private String notes;
        private String performedAt;
    }
}
