package com.empmgmt.service;

import com.empmgmt.dto.VoucherAuditDTO;
import com.empmgmt.entity.AdminNotification;
import com.empmgmt.repository.AdminNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds the once-a-day "voucher numbers still missing" summary for the admin bell.
 * Deliberately a digest, not a per-save alert: paper books are keyed out of order (sometimes two
 * books at once), so a freshly skipped number usually fills in on its own. Only numbers that have
 * stayed missing for {@link #GRACE_DAYS} days and have not been acknowledged on the Voucher Audit
 * page are reported. Nothing is sent when there are none. Receipt series only: sales voucher
 * numbers come from the Tally import, not from staff keying them in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherGapDigestService {

    static final int GRACE_DAYS = 2;
    private static final int MAX_RUNS_LISTED = 15;
    private static final int MAX_MESSAGE = 500;

    private final VoucherAuditService voucherAuditService;
    private final AdminNotificationRepository notificationRepository;

    /** @return true if a notification was created */
    public boolean sendDigest() {
        List<VoucherAuditDTO.Run> runs = voucherAuditService.findOverdueMissing(
                VoucherAuditDTO.Series.RECEIPT, GRACE_DAYS);
        if (runs.isEmpty()) {
            log.info("Voucher gap digest: nothing overdue, no notification sent");
            return false;
        }
        long total = runs.stream().mapToLong(r -> r.getEnd() - r.getStart() + 1).sum();

        StringBuilder list = new StringBuilder();
        int shown = 0;
        for (VoucherAuditDTO.Run r : runs) {
            if (shown == MAX_RUNS_LISTED) break;
            if (shown > 0) list.append(", ");
            list.append(r.getStart() == r.getEnd() ? String.valueOf(r.getStart()) : r.getStart() + "-" + r.getEnd());
            shown++;
        }
        String more = runs.size() > shown ? " and " + (runs.size() - shown) + " more range(s)" : "";
        String message = "Receipt voucher gaps: " + total + " number" + (total == 1 ? "" : "s")
                + " still missing for " + GRACE_DAYS + "+ days and not acknowledged - " + list + more
                + ". Review on the Voucher Audit page.";
        if (message.length() > MAX_MESSAGE) message = message.substring(0, MAX_MESSAGE - 3) + "...";

        notificationRepository.save(AdminNotification.builder()
                .type(AdminNotification.NotificationType.VOUCHER_GAP_DIGEST)
                .message(message)
                .triggeredBy("SCHEDULER")
                .triggeredByRole("SYSTEM")
                .sourceType("VOUCHER_AUDIT")
                .read(false)
                .build());
        log.info("Voucher gap digest sent: {} missing number(s) in {} range(s)", total, runs.size());
        return true;
    }
}
