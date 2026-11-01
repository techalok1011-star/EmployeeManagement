package com.empmgmt.monitoring;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny in-memory request statistics: which endpoints are slow and which are failing.
 * <p>
 * Built for a 512 MB instance, so it keeps <b>counts, not requests</b>:
 * <ul>
 *   <li>per endpoint (e.g. {@code GET /admin/entries/{id}/edit}): 24 hourly buckets, each a handful of
 *       counters plus a fixed 9-bin latency histogram (percentiles are estimated from the bin edges);</li>
 *   <li>one global ring of 60 one-minute buckets, used for the 5xx spike alert;</li>
 *   <li>the last {@value #MAX_RECENT_ERRORS} server errors (path without query string, status, time, trace id).</li>
 * </ul>
 * Everything is bounded (at most {@value #MAX_ROUTES} endpoints), so memory stays well under a megabyte.
 * Numbers reset when the instance restarts or sleeps - this is for spotting problems as they happen, not history.
 */
@Component
public class RequestMetrics {

    /** Upper edges (ms) of the latency histogram bins; a 9th bin holds everything slower than the last edge. */
    static final long[] EDGES_MS = {100, 250, 500, 1_000, 2_000, 3_000, 5_000, 10_000};
    static final long SLOW_MS = 3_000;
    static final int HOURS = 24;
    static final int MAX_ROUTES = 400;
    static final int MAX_RECENT_ERRORS = 50;

    private static final String OTHER = "(other endpoints)";

    private final ConcurrentHashMap<String, Route> routes = new ConcurrentHashMap<>();
    private final Instant startedAt = Instant.now();

    // ── global per-minute ring (alerts) ──────────────────────────────────────
    private final long[] minStamp = new long[60];
    private final int[] minTotal = new int[60];
    private final int[] min4xx = new int[60];
    private final int[] min5xx = new int[60];
    private final Object minLock = new Object();

    // ── recent server errors ─────────────────────────────────────────────────
    private final Deque<ErrorSample> recentErrors = new ArrayDeque<>();

    // ───────────────────────────────────────────────────────────────────────
    //  recording
    // ───────────────────────────────────────────────────────────────────────

    /** @param route normalised endpoint key, e.g. "GET /admin/entries/{id}/edit" */
    public void record(String route, int status, long tookMs, String path, String traceId) {
        record(route, status, tookMs, path, traceId, System.currentTimeMillis());
    }

    void record(String route, int status, long tookMs, String path, String traceId, long nowMs) {
        Route r = routes.get(route);
        if (r == null) {
            if (routes.size() >= MAX_ROUTES) route = OTHER;
            r = routes.computeIfAbsent(route, k -> new Route());
        }
        long hour = nowMs / 3_600_000L;
        synchronized (r) {
            Bucket b = r.buckets[(int) (hour % HOURS)];
            if (b.hour != hour) b.reset(hour);
            b.count++;
            b.sumMs += tookMs;
            if (tookMs > b.maxMs) b.maxMs = tookMs;
            b.hist[binOf(tookMs)]++;
            if (status >= 500) {
                b.c5xx++;
                r.last5xxAt = nowMs;
            } else if (status >= 400) {
                b.c4xx++;
            }
        }

        long minute = nowMs / 60_000L;
        synchronized (minLock) {
            int i = (int) (minute % 60);
            if (minStamp[i] != minute) {
                minStamp[i] = minute;
                minTotal[i] = min4xx[i] = min5xx[i] = 0;
            }
            minTotal[i]++;
            if (status >= 500) min5xx[i]++;
            else if (status >= 400) min4xx[i]++;
        }

        if (status >= 500) {
            synchronized (recentErrors) {
                if (recentErrors.size() >= MAX_RECENT_ERRORS) recentErrors.removeLast();
                recentErrors.addFirst(new ErrorSample(nowMs, route, path, status, tookMs, traceId));
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    //  reading
    // ───────────────────────────────────────────────────────────────────────

    /** Number of 5xx responses in the last {@code minutes} minutes (including the current one). */
    public int serverErrorsInLast(int minutes) {
        return serverErrorsInLast(minutes, System.currentTimeMillis());
    }

    int serverErrorsInLast(int minutes, long nowMs) {
        long minute = nowMs / 60_000L;
        int sum = 0;
        synchronized (minLock) {
            for (int i = 0; i < 60; i++) {
                if (minStamp[i] > minute - minutes && minStamp[i] <= minute) sum += min5xx[i];
            }
        }
        return sum;
    }

    /** The endpoint with the most 5xx responses in the last {@code minutes} minutes (for the alert text), or null. */
    public String worstFailingRoute(int minutes) {
        long nowMs = System.currentTimeMillis();
        long hourNow = nowMs / 3_600_000L;
        long since = nowMs - minutes * 60_000L;
        String worst = null;
        int worstCount = 0;
        for (Map.Entry<String, Route> e : routes.entrySet()) {
            Route r = e.getValue();
            synchronized (r) {
                if (r.last5xxAt < since) continue;
                int c = 0;
                for (Bucket b : r.buckets) if (b.hour >= hourNow - 1) c += b.c5xx;
                if (c > worstCount) { worstCount = c; worst = e.getKey(); }
            }
        }
        return worst;
    }

    public List<RouteRow> snapshot(int hours) {
        return snapshot(hours, System.currentTimeMillis());
    }

    List<RouteRow> snapshot(int hours, long nowMs) {
        hours = Math.max(1, Math.min(HOURS, hours));
        long hourNow = nowMs / 3_600_000L;
        List<RouteRow> rows = new ArrayList<>();
        for (Map.Entry<String, Route> e : routes.entrySet()) {
            Route r = e.getValue();
            long count = 0, sum = 0, max = 0, c4 = 0, c5 = 0;
            long[] hist = new long[EDGES_MS.length + 1];
            long last5xx;
            synchronized (r) {
                for (Bucket b : r.buckets) {
                    if (b.hour <= hourNow - hours || b.hour > hourNow) continue;
                    count += b.count; sum += b.sumMs; c4 += b.c4xx; c5 += b.c5xx;
                    if (b.maxMs > max) max = b.maxMs;
                    for (int i = 0; i < hist.length; i++) hist[i] += b.hist[i];
                }
                last5xx = r.last5xxAt;
            }
            if (count == 0) continue;
            rows.add(new RouteRow(e.getKey(), count, sum / (double) count, percentile(hist, count, 0.50, max),
                    percentile(hist, count, 0.95, max), max, c4, c5, last5xx));
        }
        rows.sort(Comparator.comparingLong(RouteRow::p95Ms).reversed().thenComparing(RouteRow::requests, Comparator.reverseOrder()));
        return rows;
    }

    public Summary summary(int hours) {
        List<RouteRow> rows = snapshot(hours);
        long total = 0, c4 = 0, c5 = 0, slow = 0;
        double sum = 0;
        for (RouteRow r : rows) {
            total += r.requests();
            c4 += r.errors4xx();
            c5 += r.errors5xx();
            sum += r.avgMs() * r.requests();
            if (r.p95Ms() >= SLOW_MS) slow++;
        }
        return new Summary(total, c4, c5, slow, total == 0 ? 0 : sum / total, startedAt);
    }

    public List<ErrorSample> recentErrors() {
        synchronized (recentErrors) {
            return new ArrayList<>(recentErrors);
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    //  helpers
    // ───────────────────────────────────────────────────────────────────────

    static int binOf(long ms) {
        for (int i = 0; i < EDGES_MS.length; i++) if (ms <= EDGES_MS[i]) return i;
        return EDGES_MS.length;
    }

    /** Estimated percentile: the upper edge of the bin that holds it (the real maximum for the slowest bin). */
    static long percentile(long[] hist, long total, double p, long observedMax) {
        long target = (long) Math.ceil(total * p);
        long run = 0;
        for (int i = 0; i < hist.length; i++) {
            run += hist[i];
            if (run >= target) return i < EDGES_MS.length ? Math.min(EDGES_MS[i], Math.max(observedMax, 1)) : observedMax;
        }
        return observedMax;
    }

    /** Collapses ids so one endpoint is one row: /admin/entries/123/edit -> /admin/entries/{id}/edit. */
    public static String normalisePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        String[] parts = path.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (i > 0) sb.append('/');
            if (!s.isEmpty() && (s.chars().allMatch(Character::isDigit) || s.matches("[0-9a-fA-F-]{16,}"))) sb.append("{id}");
            else sb.append(s);
        }
        return sb.length() > 120 ? sb.substring(0, 120) : sb.toString();
    }

    // ───────────────────────────────────────────────────────────────────────
    //  types
    // ───────────────────────────────────────────────────────────────────────

    private static final class Route {
        final Bucket[] buckets = new Bucket[HOURS];
        long last5xxAt;
        Route() { for (int i = 0; i < HOURS; i++) buckets[i] = new Bucket(); }
    }

    private static final class Bucket {
        long hour = -1;
        long count, sumMs, maxMs, c4xx, c5xx;
        final int[] hist = new int[EDGES_MS.length + 1];
        void reset(long h) {
            hour = h; count = sumMs = maxMs = c4xx = c5xx = 0;
            java.util.Arrays.fill(hist, 0);
        }
    }

    public record RouteRow(String route, long requests, double avgMs, long p50Ms, long p95Ms, long maxMs,
                           long errors4xx, long errors5xx, long last5xxAtMs) {}

    public record Summary(long requests, long errors4xx, long errors5xx, long slowEndpoints, double avgMs, Instant startedAt) {}

    public record ErrorSample(long atMs, String route, String path, int status, long tookMs, String traceId) {}
}
