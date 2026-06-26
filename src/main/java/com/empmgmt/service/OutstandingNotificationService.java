package com.empmgmt.service;

import com.empmgmt.dto.PartyOutstandingDTO;
import com.empmgmt.entity.NotificationLog;
import com.empmgmt.entity.NotificationLog.Status;
import com.empmgmt.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates daily WhatsApp payment reminders.
 *
 * Eligibility rules:
 *   ✅  outstanding > 0
 *   ✅  party has a phone number set
 *   ✅  whatsappOptIn = true
 *   ❌  skip zero / credit outstanding
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutstandingNotificationService {

    private final InvoiceService invoiceService;
    private final WhatsAppService whatsAppService;
    private final NotificationLogRepository notificationLogRepository;

    /**
     * @param triggeredBy "SCHEDULER" | "ADMIN:<username>"
     * @return list of persisted NotificationLog records (one per eligible party)
     */
    @Transactional
    public List<NotificationLog> sendDailyReminders(String triggeredBy) {
        List<PartyOutstandingDTO> summaries = invoiceService.getPartyOutstandingSummary();
        List<NotificationLog> results = new ArrayList<>();
        LocalDate today = LocalDate.now();

        int total = 0, sent = 0, skipped = 0;

        for (PartyOutstandingDTO p : summaries) {
            total++;

            // Skip zero / credit
            if (p.getOutstanding() == null
                    || p.getOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                continue;
            }
            // Skip no phone
            if (p.getPhone() == null || p.getPhone().isBlank()) {
                log.debug("[Notif] Skipping {} — no phone number", p.getDisplayName());
                skipped++;
                continue;
            }
            // Skip opted-out
            if (!p.isWhatsappOptIn()) {
                log.debug("[Notif] Skipping {} — not opted in", p.getDisplayName());
                skipped++;
                continue;
            }

            WhatsAppService.SendResult result = whatsAppService.sendPaymentReminder(
                    p.getPhone(), p.getDisplayName(), p.getOutstanding(), today);

            Status status = result.isDryRun() ? Status.DRY_RUN
                          : result.isSuccess() ? Status.SENT
                          : Status.FAILED;

            NotificationLog logEntry = NotificationLog.builder()
                    .partyName(p.getPartyName())
                    .phone(p.getPhone())
                    .outstandingAmount(p.getOutstanding())
                    .status(status)
                    .errorMessage(result.getError())
                    .triggeredBy(triggeredBy)
                    .sentAt(LocalDateTime.now())
                    .build();
            results.add(notificationLogRepository.save(logEntry));
            sent++;
        }

        log.info("[Notif] Run complete by={} — total={} sent={} skipped={}",
                triggeredBy, total, sent, skipped);
        return results;
    }
}
