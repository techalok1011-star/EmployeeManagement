package com.empmgmt.scheduler;

import com.empmgmt.service.OutstandingNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final OutstandingNotificationService notificationService;

    /**
     * Fires every day at 17:00 (5 PM) IST — admin can turn this on/off from the
     * Notifications page without a redeploy. Zone is explicit because the container
     * (Render/Docker, eclipse-temurin base image) defaults to UTC, not IST — without
     * this, "17:00" resolved to 22:30 IST, right when the free-tier instance is
     * already asleep for the night, so the job silently never ran most days.
     */
    @Scheduled(cron = "0 0 17 * * *", zone = "Asia/Kolkata")
    public void runDailyReminders() {
        if (!notificationService.isDailyReminderEnabled()) {
            log.info("[Scheduler] Daily reminders are turned OFF by admin — skipping today's run.");
            return;
        }
        log.info("[Scheduler] Triggering daily WhatsApp outstanding reminders...");
        notificationService.sendDailyReminders("SCHEDULER");
    }
}
