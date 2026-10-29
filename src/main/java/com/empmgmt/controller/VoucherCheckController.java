package com.empmgmt.controller;

import com.empmgmt.dto.VoucherAuditDTO;
import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.service.VoucherAuditService;
import com.empmgmt.util.IstClock;
import com.empmgmt.util.VoucherNumberParser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Response: {@code type} is "DUPLICATE", "SPLIT", "SUSPECT" or "OK". SPLIT means the number is already
 * used for the same party on the same day in a different payment mode - one receipt paid part cash,
 * part net banking - which the form should confirm softly rather than warn about.
 * <p>
 * {@code party}, {@code date} and {@code mode} are optional; without them the answer is DUPLICATE
 * whenever the number is already used. If party and mode are given but no date, today (IST) is assumed,
 * since employees can only enter today's collections.
 */
@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
public class VoucherCheckController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final VoucherAuditService voucherAuditService;

    @GetMapping("/receipt/check")
    public Map<String, Object> checkReceipt(@RequestParam String vch,
                                            @RequestParam(required = false) Long excludeId,
                                            @RequestParam(required = false) String party,
                                            @RequestParam(required = false) String date,
                                            @RequestParam(required = false) String mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "OK");
        result.put("matches", List.of());

        Long number = VoucherNumberParser.extractNumber(vch).orElse(null);
        if (number == null) return result;
        result.put("number", number);

        String partyName = party != null && !party.isBlank() ? party.trim() : null;
        String modeName = mode != null && !mode.isBlank() ? mode.trim().toUpperCase() : null;
        LocalDate entryDate = null;
        if (date != null && !date.isBlank()) {
            try { entryDate = LocalDate.parse(date.trim()); } catch (Exception ignored) { /* treated as unknown */ }
        } else if (partyName != null && modeName != null) {
            entryDate = IstClock.today();
        }
        // an unknown mode value must never be treated as "different from every existing mode"
        if (modeName != null) {
            try { PaymentEntry.ModeOfPayment.valueOf(modeName); } catch (IllegalArgumentException e) { modeName = null; }
        }

        voucherAuditService.previewNewVoucher(VoucherAuditDTO.Series.RECEIPT, number, excludeId, partyName, entryDate, modeName)
                .ifPresent(p -> {
                    result.put("type", p.type());
                    result.put("highest", p.highestUsed());
                    BigDecimal total = BigDecimal.ZERO;
                    List<Map<String, Object>> matches = new java.util.ArrayList<>();
                    for (VoucherAuditService.Match o : p.others()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("party", o.partyName());
                        m.put("date", o.date().format(DATE_FMT));
                        m.put("amount", o.amount());
                        m.put("mode", displayMode(o.mode()));
                        matches.add(m);
                        total = total.add(o.amount());
                    }
                    result.put("matches", matches);
                    result.put("total", total);
                });
        return result;
    }

    private static String displayMode(String mode) {
        if (mode == null) return null;
        try { return PaymentEntry.ModeOfPayment.valueOf(mode).getDisplayName(); } catch (IllegalArgumentException e) { return mode; }
    }
}
