package com.empmgmt.monitoring;

import com.empmgmt.entity.AdminNotification;
import com.empmgmt.repository.AdminNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Raises one admin bell notification when server errors (HTTP 5xx) spike: at least
 * {@code monitoring.alert.errors-threshold} failed requests inside the last
 * {@code monitoring.alert.window-minutes} minutes. After alerting it stays quiet for
 * {@code monitoring.alert.cooldown-minutes} so a long outage is one alert, not hundreds.
 * <p>
 * The state lives in memory, so after a restart the first spike alerts again - which is what you want
 * when the restart itself was the incident. If the database is the thing that is failing, the bell row
 * cannot be saved; the Performance page still shows the alert as active, and the log line is written.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ErrorSpikeAlert {

    private final RequestMetrics metrics;
    private final AdminNotificationRepository notificationRepository;

    @Value("${monitoring.alert.errors-threshold:5}")
    private int threshold;
    @Value("${monitoring.alert.window-minutes:5}")
    private int windowMinutes;
    @Value("${monitoring.alert.cooldown-minutes:30}")
    private int cooldownMinutes;

    private volatile long lastAlertAtMs;
    private volatile String lastAlertText;

    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void check() {
        try {
            int errors = metrics.serverErrorsInLast(windowMinutes);
            if (errors < threshold) return;
            long now = System.currentTimeMillis();
            if (now - lastAlertAtMs < cooldownMinutes * 60_000L) return;

            String worst = metrics.worstFailingRoute(windowMinutes);
            String text = errors + " failed requests (server errors) in the last " + windowMinutes + " minutes"
                    + (worst != null ? ". Most affected: " + worst : "") + ". Open Performance for details.";
            lastAlertAtMs = now;
            lastAlertText = text;
            log.error("ALERT 5xx spike: {}", text);
            notificationRepository.save(AdminNotification.builder()
                    .type(AdminNotification.NotificationType.SERVER_ALERT)
                    .message(text.length() > 480 ? text.substring(0, 480) : text)
                    .triggeredBy("SYSTEM")
                    .triggeredByRole("SYSTEM")
                    .sourceType("SERVER")
                    .build());
        } catch (Exception e) {
            // never let monitoring throw into the scheduler (and expect this when the database itself is down)
            log.warn("5xx spike alert could not be stored: {}", e.toString());
        }
    }

    /** True while the spike condition holds right now (for the Performance page banner). */
    public boolean isActive() {
        return metrics.serverErrorsInLast(windowMinutes) >= threshold;
    }

    public int getThreshold() { return threshold; }
    public int getWindowMinutes() { return windowMinutes; }
    public long getLastAlertAtMs() { return lastAlertAtMs; }
    public String getLastAlertText() { return lastAlertText; }
}
