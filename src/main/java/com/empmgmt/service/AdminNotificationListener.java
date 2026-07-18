package com.empmgmt.service;

import com.empmgmt.entity.AdminNotification;
import com.empmgmt.entity.User;
import com.empmgmt.event.InvoiceCreatedEvent;
import com.empmgmt.event.PaymentEntryCreatedEvent;
import com.empmgmt.repository.AdminNotificationRepository;
import com.empmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Observer-pattern subscriber: PaymentEntryService/InvoiceService publish events
 * without knowing or caring who's listening. This is the one place that decides
 * "does admin need to hear about this" - the publishers stay decoupled from that
 * policy (e.g. suppressing self-notifications for admin's own actions).
 *
 * Listens after the originating transaction commits, so a notification is never
 * created for an entry/invoice save that later rolled back.
 *
 * REQUIRES_NEW is deliberate, not decorative: by the time an AFTER_COMMIT callback
 * runs, the original transaction's resources are still bound to the thread but
 * already committed - a plain repository save() here would silently join that
 * dead transaction and never actually persist. Each listener needs its own fresh,
 * independently-committing transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationListener {

    private final AdminNotificationRepository notificationRepository;
    private final UserRepository userRepository;

    private static final NumberFormat AMOUNT_FMT = NumberFormat.getNumberInstance(new Locale("en", "IN"));

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentEntryCreated(PaymentEntryCreatedEvent event) {
        User actor = userRepository.findByUsername(event.getActorUsername()).orElse(null);
        if (actor == null || actor.getRole() == User.Role.ADMIN) {
            return; // no self-notifications for admin's own actions
        }

        var entry = event.getEntry();
        String message = actor.getFullName() + " logged a ₹" + AMOUNT_FMT.format(entry.getAmount())
                + " collection from " + entry.getPartyName();

        notificationRepository.save(AdminNotification.builder()
                .type(AdminNotification.NotificationType.COLLECTION_ADDED)
                .message(message)
                .partyName(entry.getPartyName())
                .amount(entry.getAmount())
                .triggeredBy(actor.getUsername())
                .triggeredByRole(actor.getRole().name())
                .sourceType("PAYMENT_ENTRY")
                .sourceId(entry.getId())
                .read(false)
                .build());
        log.info("🔔 Admin notification created for collection entry id={}", entry.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInvoiceCreated(InvoiceCreatedEvent event) {
        User actor = userRepository.findByUsername(event.getActorUsername()).orElse(null);
        if (actor == null || actor.getRole() == User.Role.ADMIN) {
            return; // no self-notifications for admin's own actions
        }

        var invoice = event.getInvoice();
        String message = actor.getFullName() + " added invoice " + invoice.getInvoiceNumber()
                + " for ₹" + AMOUNT_FMT.format(invoice.getAmount())
                + " (" + invoice.getPartyName() + ")";

        notificationRepository.save(AdminNotification.builder()
                .type(AdminNotification.NotificationType.INVOICE_ADDED)
                .message(message)
                .partyName(invoice.getPartyName())
                .amount(invoice.getAmount())
                .triggeredBy(actor.getUsername())
                .triggeredByRole(actor.getRole().name())
                .sourceType("INVOICE")
                .sourceId(invoice.getId())
                .read(false)
                .build());
        log.info("🔔 Admin notification created for invoice id={}", invoice.getId());
    }
}
