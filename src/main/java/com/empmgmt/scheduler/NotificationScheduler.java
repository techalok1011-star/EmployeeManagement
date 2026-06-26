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
     * Fires every day at 18:00 (6 PM) server time.
     * Change zone with spring.task.scheduling.pool.size + zone config if needed.
     */
    @Scheduled(cron = "0 0 18 * * *")
    public void runDailyReminders() {
        log.info("[Scheduler] Triggering daily WhatsApp outstanding reminders...");
        notificationService.sendDailyReminders("SCHEDULER");
    }
}
