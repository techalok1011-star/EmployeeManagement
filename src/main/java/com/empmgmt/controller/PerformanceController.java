package com.empmgmt.controller;

import com.empmgmt.monitoring.ErrorSpikeAlert;
import com.empmgmt.monitoring.RequestMetrics;
import com.empmgmt.util.IstClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin "Performance" page: which endpoints are slow, which are failing, and whether the 5xx spike alert is
 * currently firing. Numbers come from {@link RequestMetrics} (in memory, since the last restart).
 */
@Controller
@RequestMapping("/admin/performance")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PerformanceController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM, hh:mm:ss a").withZone(IstClock.ZONE);

    private final RequestMetrics metrics;
    private final ErrorSpikeAlert alert;

    public record Row(String route, long requests, String avg, String p50, String p95, String max,
                      long errors4xx, long errors5xx, String last5xx, String level) {}

    public record ErrorRow(String at, String route, String path, int status, String took, String traceId) {}

    @GetMapping
    public String page(@RequestParam(defaultValue = "24") int hours, Model model, Authentication auth) {
        int h = hours <= 1 ? 1 : hours <= 6 ? 6 : 24;
        List<Row> rows = metrics.snapshot(h).stream().map(r -> new Row(
                r.route(), r.requests(), ms(r.avgMs()), ms(r.p50Ms()), ms(r.p95Ms()), ms(r.maxMs()),
                r.errors4xx(), r.errors5xx(),
                r.last5xxAtMs() > 0 ? TIME.format(Instant.ofEpochMilli(r.last5xxAtMs())) : "—",
                r.errors5xx() > 0 ? "err" : r.p95Ms() >= 3_000 ? "slow" : r.p95Ms() >= 1_000 ? "warn" : "ok")).toList();
        List<ErrorRow> errors = metrics.recentErrors().stream().map(e -> new ErrorRow(
                TIME.format(Instant.ofEpochMilli(e.atMs())), e.route(), e.path(), e.status(), ms(e.tookMs()), e.traceId())).toList();

        model.addAttribute("adminName", auth.getName());
        model.addAttribute("hours", h);
        model.addAttribute("rows", rows);
        model.addAttribute("errors", errors);
        model.addAttribute("summary", metrics.summary(h));
        model.addAttribute("avg", ms(metrics.summary(h).avgMs()));
        model.addAttribute("startedAt", TIME.format(metrics.summary(h).startedAt()));
        model.addAttribute("alertActive", alert.isActive());
        model.addAttribute("alertThreshold", alert.getThreshold());
        model.addAttribute("alertWindow", alert.getWindowMinutes());
        model.addAttribute("lastAlertText", alert.getLastAlertText());
        model.addAttribute("lastAlertAt", alert.getLastAlertAtMs() > 0 ? TIME.format(Instant.ofEpochMilli(alert.getLastAlertAtMs())) : null);
        return "admin/performance";
    }

    /** 850 ms -> "850 ms"; 2300 ms -> "2.3 s". */
    static String ms(double v) {
        return v < 1000 ? Math.round(v) + " ms" : String.format("%.1f s", v / 1000.0);
    }
}
