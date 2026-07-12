package com.empmgmt.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationStatsDTO {

    private long totalSent;
    private long totalFailed;
    private long totalDryRun;

    /** SENT / (SENT + FAILED) * 100 — dry runs excluded since they're not real attempts. 0 if no real attempts yet. */
    private double successRatePercent;

    private long eligiblePartyCount;     // opted in AND has a phone
    private long partiesWithPhoneCount;  // has a phone, regardless of opt-in

    private List<FailureReason> topFailureReasons; // last 30 days, most common first

    /** Last 14 days, oldest first. */
    private List<String> dailyLabels;
    private List<Long> dailySentCounts;
    private List<Long> dailyFailedCounts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailureReason {
        private String reason;
        private long count;
    }
}
