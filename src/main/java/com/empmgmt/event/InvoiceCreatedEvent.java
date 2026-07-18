package com.empmgmt.event;

import com.empmgmt.entity.Invoice;
import lombok.Getter;

/**
 * Published whenever an invoice is saved, regardless of whether the creator was
 * an admin, accountant, or manager. Carries only the invoice and who performed
 * the action - listeners decide what, if anything, to do with that
 * (see {@link com.empmgmt.service.AdminNotificationListener}).
 */
@Getter
public class InvoiceCreatedEvent {

    private final Invoice invoice;
    private final String actorUsername;

    public InvoiceCreatedEvent(Invoice invoice, String actorUsername) {
        this.invoice = invoice;
        this.actorUsername = actorUsername;
    }
}
