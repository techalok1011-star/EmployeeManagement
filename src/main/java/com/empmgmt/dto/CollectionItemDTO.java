package com.empmgmt.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionItemDTO {

    private Long partyId;
    private String partyName;   // combined key
    private String displayName;
    private String gstin;

    private BigDecimal outstanding;
    private long daysOverdue;
    private String bucket; // "0-30" | "31-60" | "61-90" | "90+"

    private String phone;
    private boolean whatsappOptIn;
    private boolean eligibleForReminder; // outstanding>0 && phone present && opted in

    /** Null if never reminded. */
    private LocalDateTime lastReminderSentAt;
    /** SENT | FAILED | DRY_RUN | null */
    private String lastReminderStatus;
}
