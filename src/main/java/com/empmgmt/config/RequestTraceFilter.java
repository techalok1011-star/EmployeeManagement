package com.empmgmt.config;

import com.empmgmt.monitoring.RequestMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stamps every request with a short trace id, exposed to logs via SLF4J's MDC (see
 * logback-spring.xml's {@code %X{traceId}}) and to the error page as a request attribute.
 * When a user reports a problem, the id shown on the error page is enough to find the exact
 * request in Render's logs - previously an error was an unlinkable "something broke."
 * <p>
 * Deliberately cheap, because this runs on every single request on a 0.1-vCPU instance:
 * <ul>
 *   <li>Static assets are skipped entirely - they're the bulk of requests and never interesting.</li>
 *   <li>The id is 8 hex chars from a thread-local RNG, not a full {@code UUID.randomUUID()}
 *       (which draws from the shared secure random and can block under contention).</li>
 *   <li><b>Only slow or failed requests are logged.</b> Logging every request would multiply
 *       log volume by ~100x for no diagnostic gain and cost real money/space on a hosted plan.</li>
 *   <li>MDC put/remove is a plain ThreadLocal map write - nanoseconds, and the {@code finally}
 *       block guarantees cleanup so ids never leak across pooled request threads.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
@RequiredArgsConstructor
public class RequestTraceFilter extends OncePerRequestFilter {

    /** Per-endpoint timing / error counts for the admin Performance page and the 5xx spike alert. */
    private final RequestMetrics metrics;

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_ATTRIBUTE = "com.empmgmt.traceId";

    /** Requests slower than this get an INFO line, so a creeping performance problem is visible before users report it. */
    private static final long SLOW_REQUEST_MS = 3_000L;

    private static final String HEX = "0123456789abcdef";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/icons/") || path.equals("/sw.js") || path.equals("/favicon.ico")
                || path.endsWith("manifest.json");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = newTraceId();
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long tookMs = System.currentTimeMillis() - start;
            int status = response.getStatus();
            // Only the interesting requests reach the log: server errors, then slow ones.
            if (status >= 500) {
                log.error("{} {} -> {} in {}ms", request.getMethod(), pathWithQuery(request), status, tookMs);
            } else if (tookMs > SLOW_REQUEST_MS) {
                log.info("SLOW {} {} -> {} in {}ms", request.getMethod(), pathWithQuery(request), status, tookMs);
            }
            recordMetrics(request, status, tookMs, traceId);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    /** Never allowed to break a request: statistics are best-effort. */
    private void recordMetrics(HttpServletRequest request, int status, long tookMs, String traceId) {
        try {
            String uri = request.getRequestURI();
            if ("/health".equals(uri)) return; // the keep-alive ping would drown every real number
            String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String errorUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            String route = request.getMethod() + " " + ((pattern != null && !pattern.equals("/error") && !pattern.equals("/**"))
                    ? pattern
                    : RequestMetrics.normalisePath(errorUri != null ? errorUri : uri));
            metrics.record(route, status, tookMs, errorUri != null ? errorUri : uri, traceId);
        } catch (RuntimeException e) {
            log.debug("metrics skipped: {}", e.toString());
        }
    }

    public static String pathWithQuery(HttpServletRequest request) {
        String q = request.getQueryString();
        return q == null ? request.getRequestURI() : request.getRequestURI() + "?" + q;
    }

    private static String newTraceId() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        char[] out = new char[8];
        for (int i = 0; i < 8; i++) {
            out[i] = HEX.charAt(rnd.nextInt(16));
        }
        return new String(out);
    }
}
