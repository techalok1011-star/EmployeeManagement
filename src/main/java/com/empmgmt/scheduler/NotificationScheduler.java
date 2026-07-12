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
     * Fires every day at 17:00 (5 PM) server time — admin can turn this on/off
     * from the Notifications page without a redeploy.
     */
    @Scheduled(cron = "0 0 17 * * *")
    public void runDailyReminders() {
        if (!notificationService.isDailyReminderEnabled()) {
            log.info("[Scheduler] Daily reminders are turned OFF by admin — skipping today's run.");
            return;
        }
        log.info("[Scheduler] Triggering daily WhatsApp outstanding reminders...");
        notificationService.sendDailyReminders("SCHEDULER");
    }
}
