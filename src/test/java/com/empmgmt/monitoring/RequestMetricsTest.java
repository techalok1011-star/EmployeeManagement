package com.empmgmt.monitoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequestMetricsTest {

    private static final long T0 = 1_800_000_000_000L; // some fixed instant
    private static final long MIN = 60_000L;
    private static final long HOUR = 3_600_000L;

    @Test
    void ranksSlowestEndpointFirst() {
        RequestMetrics m = new RequestMetrics();
        for (int i = 0; i < 20; i++) m.record("GET /admin/dashboard", 200, 150, "/admin/dashboard", "t", T0);
        for (int i = 0; i < 20; i++) m.record("GET /admin/full-ledger", 200, 4_000, "/admin/full-ledger", "t", T0);
        List<RequestMetrics.RouteRow> rows = m.snapshot(1, T0);
        assertEquals("GET /admin/full-ledger", rows.get(0).route());
        assertTrue(rows.get(0).p95Ms() >= 3_000, "slow endpoint's 95th percentile should be in the slow range");
        assertTrue(rows.get(1).p95Ms() <= 250, "fast endpoint's 95th percentile stays small");
        assertEquals(20, rows.get(0).requests());
    }

    @Test
    void p95IgnoresOneOutlierButMaxKeepsIt() {
        RequestMetrics m = new RequestMetrics();
        for (int i = 0; i < 99; i++) m.record("GET /x", 200, 80, "/x", "t", T0);
        m.record("GET /x", 200, 9_000, "/x", "t", T0);
        RequestMetrics.RouteRow r = m.snapshot(1, T0).get(0);
        assertTrue(r.p95Ms() <= 100, "one slow request in 100 must not drag the 95th percentile up");
        assertEquals(9_000, r.maxMs());
    }

    @Test
    void separatesClientAndServerErrors() {
        RequestMetrics m = new RequestMetrics();
        m.record("GET /a", 404, 10, "/a", "t", T0);
        m.record("GET /a", 403, 10, "/a", "t", T0);
        m.record("GET /a", 500, 10, "/a", "t", T0);
        m.record("GET /a", 200, 10, "/a", "t", T0);
        RequestMetrics.RouteRow r = m.snapshot(1, T0).get(0);
        assertEquals(2, r.errors4xx());
        assertEquals(1, r.errors5xx());
        assertEquals(4, r.requests());
    }

    @Test
    void serverErrorWindowCountsOnlyTheLastMinutes() {
        RequestMetrics m = new RequestMetrics();
        for (int i = 0; i < 5; i++) m.record("GET /a", 500, 10, "/a", "t", T0);
        assertEquals(5, m.serverErrorsInLast(5, T0));
        assertEquals(5, m.serverErrorsInLast(5, T0 + 4 * MIN), "still inside the 5-minute window");
        assertEquals(0, m.serverErrorsInLast(5, T0 + 6 * MIN), "older than 5 minutes must not count");
        m.record("GET /a", 200, 10, "/a", "t", T0 + 6 * MIN);
        assertEquals(0, m.serverErrorsInLast(5, T0 + 6 * MIN), "successes never count as errors");
    }

    @Test
    void errorsSpreadOverMinutesAddUp() {
        RequestMetrics m = new RequestMetrics();
        for (int i = 0; i < 4; i++) m.record("GET /a", 503, 10, "/a", "t", T0 + i * MIN);
        assertEquals(4, m.serverErrorsInLast(5, T0 + 3 * MIN));
        assertEquals(2, m.serverErrorsInLast(2, T0 + 3 * MIN), "a 2-minute window sees only the last two");
    }

    @Test
    void oldHoursRollOffTheWindow() {
        RequestMetrics m = new RequestMetrics();
        m.record("GET /a", 200, 10, "/a", "t", T0);
        assertEquals(1, m.snapshot(24, T0 + 2 * HOUR).size(), "two hours later it is still inside 24 h");
        assertTrue(m.snapshot(1, T0 + 2 * HOUR).isEmpty(), "but outside a 1 h window");
        // same slot 24 hours later: the old bucket is reset, not mixed in
        m.record("GET /a", 200, 10, "/a", "t", T0 + 24 * HOUR);
        assertEquals(1, m.snapshot(1, T0 + 24 * HOUR).get(0).requests());
    }

    @Test
    void numberOfEndpointsIsBounded() {
        RequestMetrics m = new RequestMetrics();
        for (int i = 0; i < RequestMetrics.MAX_ROUTES + 200; i++) m.record("GET /r" + i, 200, 10, "/r" + i, "t", T0);
        assertTrue(m.snapshot(1, T0).size() <= RequestMetrics.MAX_ROUTES + 1, "extra endpoints fold into one catch-all row");
    }

    @Test
    void keepsOnlyTheMostRecentServerErrors() {
        RequestMetrics m = new RequestMetrics();
        for (int i = 0; i < RequestMetrics.MAX_RECENT_ERRORS + 25; i++) m.record("GET /a", 500, 10, "/a" + i, "id" + i, T0 + i);
        List<RequestMetrics.ErrorSample> e = m.recentErrors();
        assertEquals(RequestMetrics.MAX_RECENT_ERRORS, e.size());
        assertEquals("id" + (RequestMetrics.MAX_RECENT_ERRORS + 24), e.get(0).traceId(), "newest first");
    }

    @Test
    void collapsesIdsInPaths() {
        assertEquals("/admin/entries/{id}/edit", RequestMetrics.normalisePath("/admin/entries/10537/edit"));
        assertEquals("/manager/parties/{id}/edit", RequestMetrics.normalisePath("/manager/parties/753/edit"));
        assertEquals("/admin/full-ledger", RequestMetrics.normalisePath("/admin/full-ledger"));
        assertEquals("/", RequestMetrics.normalisePath(""));
    }

    @Test
    void summaryTotalsAcrossEndpoints() {
        RequestMetrics m = new RequestMetrics();
        m.record("GET /a", 200, 100, "/a", "t", System.currentTimeMillis());
        m.record("GET /b", 500, 5_000, "/b", "t", System.currentTimeMillis());
        RequestMetrics.Summary s = m.summary(1);
        assertEquals(2, s.requests());
        assertEquals(1, s.errors5xx());
        assertEquals(1, s.slowEndpoints());
    }
}
