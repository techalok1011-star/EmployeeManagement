package com.empmgmt.controller;

import com.empmgmt.entity.LoginSession;
import com.empmgmt.repository.LoginSessionRepository;
import com.empmgmt.repository.PaymentEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only map of where collections were made (and where staff logged in from). It exposes staff
 * locations, so it is ADMIN only - deliberately not widened to the accountant.
 */
@Controller
@RequestMapping("/admin/collection-map")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CollectionMapController {

    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2100, 1, 1);
    private static final int MAX_LOGINS = 3000;

    private final PaymentEntryRepository paymentEntryRepository;
    private final LoginSessionRepository loginSessionRepository;

    @GetMapping
    public String page(Model model, Authentication auth) {
        model.addAttribute("adminName", auth.getName());
        return "admin/collection-map";
    }

    @GetMapping("/data")
    @org.springframework.web.bind.annotation.ResponseBody
    public ResponseEntity<Map<String, Object>> data(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : EARLIEST;
        LocalDate t = to != null ? to : LATEST;

        List<Map<String, Object>> collections = new ArrayList<>();
        for (Object[] r : paymentEntryRepository.findMapPoints(f, t)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("party", r[1]);
            m.put("amount", r[2]);
            m.put("mode", r[3] == null ? null : r[3].toString());
            m.put("date", r[4] == null ? null : r[4].toString());
            m.put("vch", r[5]);
            m.put("by", r[6]);
            m.put("user", r[7]);
            m.put("lat", r[8]);
            m.put("lng", r[9]);
            m.put("receipt", r[10] != null);
            collections.add(m);
        }

        List<Map<String, Object>> logins = new ArrayList<>();
        List<LoginSession> sessions = loginSessionRepository.findWithLocation(
                f.atStartOfDay(), t.plusDays(1).atStartOfDay(), PageRequest.of(0, MAX_LOGINS));
        for (LoginSession s : sessions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("by", s.getFullName());
            m.put("user", s.getUsername());
            m.put("role", s.getRole());
            m.put("at", s.getLoginAt() == null ? null : s.getLoginAt().toString());
            m.put("lat", s.getLatitude());
            m.put("lng", s.getLongitude());
            logins.add(m);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collections", collections);
        body.put("logins", logins);
        body.put("totalCollections", paymentEntryRepository.countInRange(f, t));
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(body);
    }
}
