package com.empmgmt.controller;

import com.empmgmt.config.RequestTraceFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The single landing point for every error the app produces - a 404 for a mistyped URL, a 403
 * from Spring Security, or an uncaught exception mid-request. Replaces Spring Boot's default
 * "Whitelabel Error Page", which leaked framework internals and read as a crashed site.
 * <p>
 * Two rules here are deliberate:
 * <ul>
 *   <li><b>The user never sees a stack trace or exception message</b> - those can carry table
 *       names, SQL fragments, or file paths. They get a plain-language message plus the trace
 *       id; the detail goes to the server log only.</li>
 *   <li><b>Only 5xx is logged as an error.</b> 404s and 403s are routine (bots, stale
 *       bookmarks, an accountant opening an admin-only page) and logging them at ERROR would
 *       bury real failures and inflate log volume.</li>
 * </ul>
 */
@Controller
@Slf4j
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        int status = statusCode(request);
        Object traceId = request.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE);
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (status >= 500) {
            // Full stack trace, server-side only. The traceId is already on the line via MDC.
            log.error("Unhandled error rendering {} (status {})", originalPath, status, ex);
        } else if (status == 404) {
            log.debug("404 for {}", originalPath);
        }

        model.addAttribute("status", status);
        model.addAttribute("traceId", traceId != null ? traceId : "—");
        model.addAttribute("headline", headlineFor(status));
        model.addAttribute("detail", detailFor(status));
        model.addAttribute("showRetry", status >= 500 || status == 408 || status == 503);
        return "error";
    }

    private int statusCode(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer i) return i;
        return 500;
    }

    private String headlineFor(int status) {
        return switch (status) {
            case 404 -> "We couldn't find that page";
            case 403 -> "You don't have access to this";
            case 401 -> "Please sign in again";
            case 503, 502 -> "The site is temporarily unavailable";
            default -> "Something went wrong on our side";
        };
    }

    private String detailFor(int status) {
        return switch (status) {
            case 404 -> "The link may be out of date, or the page may have been moved.";
            case 403 -> "Your account doesn't have permission for this page. If you think it should, ask an admin.";
            case 401 -> "Your session has expired. Signing in again should fix it.";
            case 503, 502 -> "We're having trouble reaching the server right now. Please try again in a minute.";
            default -> "This one is on us, not you — nothing you entered was lost. Please try again in a moment.";
        };
    }
}
