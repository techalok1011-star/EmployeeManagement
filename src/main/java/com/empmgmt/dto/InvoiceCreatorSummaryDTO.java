package com.empmgmt.dto;

import lombok.*;

import java.math.BigDecimal;

/** One row per ADMIN/ACCOUNTANT/MANAGER on the "Invoices by Creator" admin page - mirrors EmployeeCollectionSummaryDTO's shape for payment entries. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceCreatorSummaryDTO {

    private Long creatorId;
    private String fullName;
    private String username;
    private String role;

    private BigDecimal todayAmount;
    private long todayCount;

    private BigDecimal monthAmount;
    private long monthCount;

    private BigDecimal allTimeAmount;
    private long allTimeCount;
}
