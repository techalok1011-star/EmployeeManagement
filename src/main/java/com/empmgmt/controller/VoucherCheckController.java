package com.empmgmt.controller;

import com.empmgmt.dto.VoucherAuditDTO;
import com.empmgmt.service.VoucherAuditService;
import com.empmgmt.util.VoucherNumberParser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry-time check for the Receipt Vch No. field, used by the popup on every collection form
 * (any logged-in role). Informational only - it never blocks a save; the silent admin-bell alert in
 * AdminNotificationListener still fires independently on save. Uses the same current-financial-year
 * classification as the Voucher Audit page so the person entering the data and the admin agree.
 * <p>
 * Response: {@code type} is "DUPLICATE", "SUSPECT" or "OK".
 */
@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
public class VoucherCheckController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final VoucherAuditService voucherAuditService;

    @GetMapping("/receipt/check")
    public Map<String, Object> checkReceipt(@RequestParam String vch,
                                            @RequestParam(required = false) Long excludeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "OK");
        result.put("matches", List.of());

        Long number = VoucherNumberParser.extractNumber(vch).orElse(null);
        if (number == null) return result;
        result.put("number", number);

        voucherAuditService.previewNewVoucher(VoucherAuditDTO.Series.RECEIPT, number, excludeId).ifPresent(p -> {
            result.put("type", p.type());
            result.put("highest", p.highestUsed());
            result.put("matches", p.others().stream().map(o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("party", o.partyName());
                m.put("date", o.date().format(DATE_FMT));
                m.put("amount", o.amount());
                return m;
            }).toList());
        });
        return result;
    }
}
