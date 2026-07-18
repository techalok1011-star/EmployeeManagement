package com.empmgmt.event;

import com.empmgmt.entity.PaymentEntry;
import lombok.Getter;

/**
 * Published whenever a payment (collection) entry is saved, regardless of which
 * flow created it (employee day-entry, or staff backfill from the party ledger).
 * Carries only the entry and who performed the action - listeners decide what,
 * if anything, to do with that (see {@link com.empmgmt.service.AdminNotificationListener}).
 */
@Getter
public class PaymentEntryCreatedEvent {

    private final PaymentEntry entry;
    private final String actorUsername;

    public PaymentEntryCreatedEvent(PaymentEntry entry, String actorUsername) {
        this.entry = entry;
        this.actorUsername = actorUsername;
    }
}
