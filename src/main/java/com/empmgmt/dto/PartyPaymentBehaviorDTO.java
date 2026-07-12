package com.empmgmt.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyPaymentBehaviorDTO {

    private Long partyId;
    private String partyName;   // combined key
    private String displayName;
    private String gstin;

    private BigDecimal outstanding;
    /** "Clear" | "Credit" | "0-30" | "31-60" | "61-90" | "90+" */
    private String currentBucket;

    /** Average days between an invoice's date and the payment that fully settled it, across historically-paid invoices. Null if none fully paid yet. */
    private Double avgDaysToPay;
    private long paidInvoiceCount;
    private long totalPaymentsCount;
    private LocalDate lastPaymentDate;

    /** "Excellent" | "Good" | "Slow" | "Chronic Late" | "No History" */
    private String behaviorLabel;

    private String phone;
    private boolean whatsappOptIn;
    private boolean eligibleForReminder;
}
