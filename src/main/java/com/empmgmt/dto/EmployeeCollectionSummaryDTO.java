package com.empmgmt.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeCollectionSummaryDTO {

    private Long employeeId;
    private String fullName;
    private String username;

    private BigDecimal todayAmount;
    private long todayCount;

    private BigDecimal monthAmount;
    private long monthCount;

    private BigDecimal allTimeAmount;
    private long allTimeCount;
}
