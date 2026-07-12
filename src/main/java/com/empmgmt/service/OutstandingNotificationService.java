package com.empmgmt.service;

import com.empmgmt.dto.CollectionItemDTO;
import com.empmgmt.dto.NotificationStatsDTO;
import com.empmgmt.dto.PartyAgingDTO;
import com.empmgmt.dto.PartyOutstandingDTO;
import com.empmgmt.entity.NotificationLog;
import com.empmgmt.entity.NotificationLog.Status;
import com.empmgmt.entity.NotificationSettings;
import com.empmgmt.repository.NotificationLogRepository;
import com.empmgmt.repository.NotificationSettingsRepository;
import com.empmgmt.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PartyRepository partyRepository;

    /**
     * @param triggeredBy "SCHEDULER" | "ADMIN:<username>"
     * @return list of persisted NotificationLog records (one per eligible party)
     */
    @Transactional
    public List<NotificationLog> sendDailyReminders(String triggeredBy) {
        List<PartyOutstandingDTO> summaries = invoiceService.getPartyOutstandingSummary();
        List<NotificationLog> results = new ArrayList<>();

        int total = 0, sent = 0, skipped = 0;

        for (PartyOutstandingDTO p : summaries) {
            total++;
            if (!isEligible(p)) {
                skipped++;
                continue;
            }
            results.add(sendAndLog(p, triggeredBy));
            sent++;
        }

        log.info("[Notif] Run complete by={} — total={} sent={} skipped={}",
                triggeredBy, total, sent, skipped);
        return results;
    }

    /**
     * Retries only the parties whose most recent notification attempt (within the
     * last 30 days) ended in FAILED. Re-checks eligibility before retrying, since
     * outstanding/opt-in may have changed since the failed attempt.
     *
     * @param triggeredBy "ADMIN:<username>"
     * @return list of newly persisted NotificationLog records
     */
    @Transactional
    public List<NotificationLog> resendFailed(String triggeredBy) {
        Set<String> failedParties = latestStatusByParty(30).entrySet().stream()
                .filter(e -> e.getValue() == Status.FAILED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (failedParties.isEmpty()) {
            return List.of();
        }

        List<NotificationLog> results = new ArrayList<>();
        for (PartyOutstandingDTO p : invoiceService.getPartyOutstandingSummary()) {
            if (!failedParties.contains(p.getPartyName()) || !isEligible(p)) {
                continue;
            }
            results.add(sendAndLog(p, triggeredBy));
        }

        log.info("[Notif] Resend-failed by={} — retried={}", triggeredBy, results.size());
        return results;
    }

    /**
     * Aggregate stats for the Notification Analytics dashboard.
     */
    @Transactional(readOnly = true)
    public NotificationStatsDTO getStats() {
        Map<Status, Long> statusCounts = notificationLogRepository.countGroupedByStatus().stream()
                .collect(Collectors.toMap(row -> (Status) row[0], row -> (Long) row[1]));

        long sent = statusCounts.getOrDefault(Status.SENT, 0L);
        long failed = statusCounts.getOrDefault(Status.FAILED, 0L);
        long dryRun = statusCounts.getOrDefault(Status.DRY_RUN, 0L);
        double successRate = (sent + failed) > 0 ? (sent * 100.0 / (sent + failed)) : 0.0;

        List<NotificationLog> recent = notificationLogRepository
                .findBySentAtAfterOrderBySentAtDesc(LocalDateTime.now().minusDays(30));

        Map<String, Long> reasonCounts = recent.stream()
                .filter(n -> n.getStatus() == Status.FAILED && n.getErrorMessage() != null && !n.getErrorMessage().isBlank())
                .collect(Collectors.groupingBy(NotificationLog::getErrorMessage, Collectors.counting()));

        List<NotificationStatsDTO.FailureReason> topReasons = reasonCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> NotificationStatsDTO.FailureReason.builder().reason(e.getKey()).count(e.getValue()).build())
                .collect(Collectors.toList());

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd MMM");
        LocalDate today = LocalDate.now();
        List<String> dailyLabels = new ArrayList<>();
        List<Long> dailySent = new ArrayList<>();
        List<Long> dailyFailed = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            dailyLabels.add(day.format(dayFmt));
            dailySent.add(recent.stream()
                    .filter(n -> n.getStatus() == Status.SENT && n.getSentAt().toLocalDate().equals(day))
                    .count());
            dailyFailed.add(recent.stream()
                    .filter(n -> n.getStatus() == Status.FAILED && n.getSentAt().toLocalDate().equals(day))
                    .count());
        }

        return NotificationStatsDTO.builder()
                .totalSent(sent)
                .totalFailed(failed)
                .totalDryRun(dryRun)
                .successRatePercent(successRate)
                .eligiblePartyCount(partyRepository.countByWhatsappOptInTrueAndPhoneIsNotNull())
                .partiesWithPhoneCount(partyRepository.countByPhoneIsNotNull())
                .topFailureReasons(topReasons)
                .dailyLabels(dailyLabels)
                .dailySentCounts(dailySent)
                .dailyFailedCounts(dailyFailed)
                .build();
    }

    /**
     * Sends a one-off reminder to a single party (used by the Collections
     * Dashboard's "Remind Now" action). Re-checks eligibility first.
     *
     * @throws NoSuchElementException if the party has no outstanding-summary row
     * @throws IllegalStateException  if the party isn't currently eligible
     */
    @Transactional
    public NotificationLog sendSingleReminder(String combinedPartyName, String triggeredBy) {
        PartyOutstandingDTO party = invoiceService.getPartyOutstandingSummary().stream()
                .filter(p -> p.getPartyName().equals(combinedPartyName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Party not found: " + combinedPartyName));

        if (!isEligible(party)) {
            throw new IllegalStateException(
                    "Party is not eligible for a reminder (no outstanding balance, no phone, or not opted in).");
        }
        return sendAndLog(party, triggeredBy);
    }

    /**
     * Worklist for the Collections Dashboard: every party with outstanding &gt; 0,
     * enriched with when they were last reminded (last 90 days) and whether
     * they're currently eligible for another reminder.
     */
    @Transactional(readOnly = true)
    public List<CollectionItemDTO> getCollectionsWorklist() {
        List<PartyAgingDTO> aging = invoiceService.getAgingReport();
        Map<String, NotificationLog> latestByParty = latestNotificationByParty(90);

        return aging.stream().map(a -> {
            NotificationLog latest = latestByParty.get(a.getPartyName());
            boolean eligible = a.getPhone() != null && !a.getPhone().isBlank() && a.isWhatsappOptIn();
            return CollectionItemDTO.builder()
                    .partyId(a.getPartyId())
                    .partyName(a.getPartyName())
                    .displayName(a.getDisplayName())
                    .gstin(a.getGstin())
                    .outstanding(a.getOutstanding())
                    .daysOverdue(a.getDaysOverdue())
                    .bucket(a.getBucket())
                    .phone(a.getPhone())
                    .whatsappOptIn(a.isWhatsappOptIn())
                    .eligibleForReminder(eligible)
                    .lastReminderSentAt(latest != null ? latest.getSentAt() : null)
                    .lastReminderStatus(latest != null ? latest.getStatus().name() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Whether the 5 PM daily scheduler should actually send reminders.
     * Defaults to enabled if no settings row exists yet.
     */
    @Transactional(readOnly = true)
    public boolean isDailyReminderEnabled() {
        return notificationSettingsRepository.findById(NotificationSettings.SETTINGS_ID)
                .map(NotificationSettings::isDailyReminderEnabled)
                .orElse(true);
    }

    /**
     * Flips the daily-scheduler on/off switch.
     *
     * @return the new enabled state, after flipping
     */
    @Transactional
    public boolean toggleDailyReminder(String updatedBy) {
        NotificationSettings settings = notificationSettingsRepository
                .findById(NotificationSettings.SETTINGS_ID)
                .orElseGet(() -> NotificationSettings.builder()
                        .id(NotificationSettings.SETTINGS_ID)
                        .dailyReminderEnabled(true)
                        .build());
        settings.setDailyReminderEnabled(!settings.isDailyReminderEnabled());
        settings.setUpdatedBy(updatedBy);
        settings.setUpdatedAt(LocalDateTime.now());
        notificationSettingsRepository.save(settings);
        return settings.isDailyReminderEnabled();
    }

    // ─────────────────────────────────────────────────────────
    // INTERNAL
    // ─────────────────────────────────────────────────────────

    private boolean isEligible(PartyOutstandingDTO p) {
        if (p.getOutstanding() == null || p.getOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (p.getPhone() == null || p.getPhone().isBlank()) {
            log.debug("[Notif] Skipping {} — no phone number", p.getDisplayName());
            return false;
        }
        if (!p.isWhatsappOptIn()) {
            log.debug("[Notif] Skipping {} — not opted in", p.getDisplayName());
            return false;
        }
        return true;
    }

    private NotificationLog sendAndLog(PartyOutstandingDTO p, String triggeredBy) {
        WhatsAppService.SendResult result = whatsAppService.sendPaymentReminder(
                p.getPhone(), p.getDisplayName(), p.getOutstanding(), LocalDate.now());

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
        return notificationLogRepository.save(logEntry);
    }

    /** Most recent status per party, considering only logs from the last {@code days} days. */
    private Map<String, Status> latestStatusByParty(int days) {
        Map<String, NotificationLog> latest = latestNotificationByParty(days);
        Map<String, Status> statuses = new LinkedHashMap<>();
        latest.forEach((party, logEntry) -> statuses.put(party, logEntry.getStatus()));
        return statuses;
    }

    /** Most recent NotificationLog per party, considering only logs from the last {@code days} days. */
    private Map<String, NotificationLog> latestNotificationByParty(int days) {
        List<NotificationLog> recent = notificationLogRepository
                .findBySentAtAfterOrderBySentAtDesc(LocalDateTime.now().minusDays(days));

        Map<String, NotificationLog> latest = new LinkedHashMap<>();
        for (NotificationLog n : recent) {
            latest.putIfAbsent(n.getPartyName(), n); // ordered desc — first seen per party is the latest
        }
        return latest;
    }
}
