package com.empmgmt.service;

import com.empmgmt.dto.VoucherAuditDTO;
import com.empmgmt.entity.AdminNotification;
import com.empmgmt.entity.User;
import com.empmgmt.event.InvoiceCreatedEvent;
import com.empmgmt.event.PartyCreatedEvent;
import com.empmgmt.event.PaymentEntryCreatedEvent;
import com.empmgmt.repository.AdminNotificationRepository;
import com.empmgmt.repository.InvoiceRepository;
import com.empmgmt.repository.PaymentEntryRepository;
import com.empmgmt.repository.UserRepository;
import com.empmgmt.util.VoucherNumberParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
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
    private final VoucherAuditService voucherAuditService;
    private final PaymentEntryRepository paymentEntryRepository;
    private final InvoiceRepository invoiceRepository;

    private static final NumberFormat AMOUNT_FMT = NumberFormat.getNumberInstance(new Locale("en", "IN"));

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentEntryCreated(PaymentEntryCreatedEvent event) {
        User actor = userRepository.findByUsername(event.getActorUsername()).orElse(null);
        var entry = event.getEntry();

        if (actor != null && actor.getRole() != User.Role.ADMIN) {
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

        // Unlike the notification above, a duplicate/suspect voucher number is a data-quality
        // problem worth knowing about even when admin logged it themselves - so this check is
        // deliberately not gated by the same "skip if actor is ADMIN" rule.
        checkVoucherAnomaly(VoucherAuditDTO.Series.RECEIPT, entry.getReceiptVchNo(), entry.getPartyName(),
                entry.getAmount(), entry.getId(), "PAYMENT_ENTRY", actor);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInvoiceCreated(InvoiceCreatedEvent event) {
        User actor = userRepository.findByUsername(event.getActorUsername()).orElse(null);
        var invoice = event.getInvoice();

        if (actor != null && actor.getRole() != User.Role.ADMIN) {
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

        checkVoucherAnomaly(VoucherAuditDTO.Series.SALES, invoice.getSalesVchNo(), invoice.getPartyName(),
                invoice.getAmount(), invoice.getId(), "INVOICE", actor);
    }

    /**
     * Tells the admin a new party exists - every new party is a potential duplicate/typo of an
     * existing one (the recurring PRASANG/SURAJ/USMANI problem), so the admin wants to see each.
     * Skipped when the admin created it themselves. Wrapped so a failure here (e.g. the
     * admin_notifications type CHECK not yet widened on a database) can never undo or fail the
     * party creation that already committed.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPartyCreated(PartyCreatedEvent event) {
        try {
            User actor = userRepository.findByUsername(event.getActorUsername()).orElse(null);
            if (actor == null || actor.getRole() == User.Role.ADMIN) return;
            var party = event.getParty();
            String message = actor.getFullName() + " (" + actor.getRole().name().toLowerCase()
                    + ") added a new party: " + party.getCombined();
            if (message.length() > 500) message = message.substring(0, 497) + "...";
            notificationRepository.save(AdminNotification.builder()
                    .type(AdminNotification.NotificationType.PARTY_ADDED)
                    .message(message)
                    .partyName(party.getCombined())
                    .triggeredBy(actor.getUsername())
                    .triggeredByRole(actor.getRole().name())
                    .sourceType("PARTY")
                    .sourceId(party.getId())
                    .read(false)
                    .build());
            log.info("🔔 Admin notification created for new party id={}", party.getId());
        } catch (Exception e) {
            log.error("New-party notification failed (party itself was created fine)", e);
        }
    }

    /**
     * Silently rings the admin bell when a just-saved voucher number is a duplicate of one
     * already used this financial year, or is far enough outside the normal range to look like a
     * typo (the same DUPLICATE/SUSPECT classification VoucherAuditService uses for the audit
     * dashboard). Deliberately does nothing for AMBIGUOUS/UNPARSEABLE text, and deliberately does
     * nothing for an ordinary missing number - those are routine and belong on the dashboard for
     * batch review, not a one-by-one interruption. The person who saved the entry is never shown
     * anything either way - this only ever reaches the admin notification feed.
     * <p>
     * Wrapped so it can never turn into a user-visible failure: this runs synchronously as part of
     * the same request that just saved the entry/invoice (AFTER_COMMIT still executes inline, not
     * on a separate thread), so an uncaught exception here would propagate back through
     * eventPublisher.publishEvent() into the controller action - meaning the entry could be saved
     * successfully in the DB while the user's screen shows an error. Same defensive principle as
     * the receipt-photo attach step in PaymentEntryService: a side effect must never blow up the
     * primary action it's attached to.
     */
    private void checkVoucherAnomaly(VoucherAuditDTO.Series series, String rawVch, String partyName,
                                      BigDecimal amount, Long sourceId, String sourceType, User actor) {
        try {
            checkVoucherAnomalyUnsafe(series, rawVch, partyName, amount, sourceId, sourceType, actor);
        } catch (Exception e) {
            log.error("Voucher anomaly check failed for {} id={} (entry itself already saved fine)",
                    sourceType, sourceId, e);
        }
    }

    private void checkVoucherAnomalyUnsafe(VoucherAuditDTO.Series series, String rawVch, String partyName,
                                            BigDecimal amount, Long sourceId, String sourceType, User actor) {
        if (rawVch == null || rawVch.isBlank()) return;
        Long vchNumber = VoucherNumberParser.extractNumber(rawVch).orElse(null);
        if (vchNumber == null) return;

        // Targeted check, not the full cached dashboard report - see checkNewVoucher's own
        // Javadoc for why: that report builds book grids and every exception category, which
        // this doesn't need and can't afford to redo on every single save.
        var anomaly = voucherAuditService.checkNewVoucher(series, vchNumber, sourceId);
        if (anomaly.isEmpty()) return;

        boolean isDuplicate = "DUPLICATE".equals(anomaly.get().type());
        String seriesLabel = series == VoucherAuditDTO.Series.RECEIPT ? "Receipt" : "Sales";
        String actorName = actor != null ? actor.getFullName() : "Someone";
        String message;
        AdminNotification.NotificationType type;
        if (isDuplicate) {
            type = AdminNotification.NotificationType.VOUCHER_DUPLICATE;
            int otherOccurrences = anomaly.get().otherOccurrences();
            message = actorName + " used " + seriesLabel + " Vch No. " + vchNumber
                    + " for " + partyName + " - already recorded " + otherOccurrences
                    + " other time" + (otherOccurrences == 1 ? "" : "s") + " this financial year"
                    + describeMatches(series, anomaly.get().others()) + ".";
            if (message.length() > 500) message = message.substring(0, 497) + "...";
        } else {
            type = AdminNotification.NotificationType.VOUCHER_SUSPECT;
            message = actorName + " used " + seriesLabel + " Vch No. " + vchNumber
                    + " for " + partyName + " - far outside this year's normal range, possible typo.";
        }

        notificationRepository.save(AdminNotification.builder()
                .type(type)
                .message(message)
                .partyName(partyName)
                .amount(amount)
                .triggeredBy(actor != null ? actor.getUsername() : null)
                .triggeredByRole(actor != null ? actor.getRole().name() : null)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .read(false)
                .build());
        log.info("🔔 Admin notification created for {} voucher {} ({}) on {} id={}",
                anomaly.get().type(), seriesLabel, vchNumber, sourceType, sourceId);
    }

    /** " (matches: <party>, <date>, ₹<amt>, entered by <name>; ...)" for the earlier rows sharing the number. */
    private String describeMatches(VoucherAuditDTO.Series series, java.util.List<VoucherAuditService.Match> others) {
        if (others == null || others.isEmpty()) return "";
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        StringBuilder sb = new StringBuilder(" (matches: ");
        for (int i = 0; i < others.size(); i++) {
            var m = others.get(i);
            if (i > 0) sb.append("; ");
            sb.append(m.partyName()).append(", ").append(m.date().format(fmt))
              .append(", ₹").append(AMOUNT_FMT.format(m.amount()))
              .append(", entered by ").append(enteredBy(series, m.id()));
        }
        return sb.append(")").toString();
    }

    private String enteredBy(VoucherAuditDTO.Series series, Long id) {
        try {
            if (series == VoucherAuditDTO.Series.RECEIPT) {
                return paymentEntryRepository.findById(id)
                        .map(p -> p.getEmployee() != null ? p.getEmployee().getFullName() : "unknown")
                        .orElse("unknown");
            }
            return invoiceRepository.findById(id)
                    .map(i -> i.getCreatedBy() == null ? "unknown (imported)"
                            : userRepository.findByUsername(i.getCreatedBy())
                                .map(User::getFullName).orElse(i.getCreatedBy()))
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }
}
