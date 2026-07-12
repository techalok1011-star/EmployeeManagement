package com.empmgmt.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutiveDashboardDTO {

    private BigDecimal totalInvoiced;   // all-time
    private BigDecimal totalCollected;  // all-time
    private BigDecimal totalOutstanding;

    private BigDecimal todayCollections;
    private BigDecimal monthCollections; // month-to-date

    private List<PartyOutstandingDTO> topDefaulters; // top 10 by outstanding, desc

    /** Last 6 months, oldest first. */
    private List<String> monthlyLabels;
    private List<BigDecimal> monthlyInvoiced;
    private List<BigDecimal> monthlyCollected;
}
