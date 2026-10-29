package com.empmgmt.scheduler;

import com.empmgmt.service.VoucherGapDigestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoucherGapDigestScheduler {

    private final VoucherGapDigestService digestService;

    /**
     * Every day at 22:00 IST. The zone is explicit because the container defaults to UTC (see
     * NotificationScheduler). The cron is overridable via voucher.gap-digest.cron mainly so it can
     * be exercised locally without waiting until 10 PM. Note the free Render instance only stays
     * awake until about 22:20 IST (external keep-alive window), so this time is inside it.
     */
    @Scheduled(cron = "${voucher.gap-digest.cron:0 0 22 * * *}", zone = "Asia/Kolkata")
    public void run() {
        try {
            digestService.sendDigest();
        } catch (Exception e) {
            log.error("Voucher gap digest failed", e);
        }
    }
}
