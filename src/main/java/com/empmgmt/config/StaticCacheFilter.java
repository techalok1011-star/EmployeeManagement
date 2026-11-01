package com.empmgmt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Lets browsers keep copies of scripts, styles and icons.
 * <p>
 * Spring Security adds {@code Cache-Control: no-store} to every response that does not already have a
 * Cache-Control header, so until now every page view re-downloaded every script and stylesheet - each one a
 * full round trip to a slow, far-away server. This filter sets the header first (Security leaves an existing
 * one alone), choosing by how safe it is to keep the file:
 * <ul>
 *   <li><b>Fingerprinted files</b> - the content hash is in the file name, so a changed file always has a new
 *       name and the old one can be kept for a year: {@code /js/theme-<md5>.js} (Spring's content-version
 *       resolver rewrites every {@code th:src}/{@code th:href}, see application.properties) and the React map's
 *       {@code /js/map-app/assets/*} (Vite names them by hash).</li>
 *   <li><b>Unversioned /js and /css</b> (a hard-coded link, or a very old cached page): {@code no-cache} - the
 *       browser may keep it but must ask first, getting a tiny 304 if it is unchanged. Never stale.</li>
 *   <li><b>Icons / images</b>: one day.</li>
 *   <li>{@code /sw.js} and manifests: {@code no-cache}, so a changed service worker is picked up.</li>
 * </ul>
 * Everything else (the pages themselves, JSON) is untouched and keeps Spring Security's {@code no-store}, which
 * is right for live financial data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class StaticCacheFilter extends OncePerRequestFilter {

    static final String FOREVER = "public, max-age=31536000, immutable";
    static final String REVALIDATE = "no-cache";
    static final String ONE_DAY = "public, max-age=86400";

    private static final Pattern FINGERPRINTED = Pattern.compile("^/(js|css)/.+-[0-9a-f]{32}\\.(js|css)$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            String value = policyFor(request.getRequestURI());
            if (value != null) response.setHeader("Cache-Control", value);
        }
        chain.doFilter(request, response);
    }

    /** Package-visible for tests: the Cache-Control value for a path, or null to leave the response alone. */
    static String policyFor(String path) {
        if (path.startsWith("/js/map-app/assets/") || FINGERPRINTED.matcher(path).matches()) return FOREVER;
        if (path.startsWith("/js/") || path.startsWith("/css/")) return REVALIDATE;
        if (path.startsWith("/icons/") || path.startsWith("/images/")) return ONE_DAY;
        if (path.equals("/sw.js") || path.endsWith("manifest.json")) return REVALIDATE;
        return null;
    }
}
