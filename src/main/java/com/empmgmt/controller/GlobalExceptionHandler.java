package com.empmgmt.controller;

import com.empmgmt.config.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Catches anything a controller throws and turns it into the shared error page, with one log
 * line carrying the context needed to actually debug it: trace id (via MDC), the URL, and who
 * was signed in. Without this the same failure produced a bare stack trace with no indication
 * of which user or which page triggered it.
 * <p>
 * {@link AccessDeniedException} is deliberately re-thrown rather than handled: Spring Security's
 * own {@code accessDeniedHandler} (see SecurityConfig) already has richer handling for it,
 * including the CSRF-expiry redirect to {@code /login?expired=true}. Swallowing it here would
 * silently break that.
 */
@ControllerAdvice
@Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE) // runs last, so any more specific handler still wins
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUncaught(Exception e, HttpServletRequest request, Model model) {
        Object traceId = request.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE);
        String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";

        log.error("Unhandled {} on {} {} (user={}) - {}",
                e.getClass().getSimpleName(),
                request.getMethod(),
                RequestTraceFilter.pathWithQuery(request),
                user,
                e.getMessage(),
                e);

        model.addAttribute("status", 500);
        model.addAttribute("traceId", traceId != null ? traceId : "—");
        model.addAttribute("headline", "Something went wrong on our side");
        model.addAttribute("detail", "This one is on us, not you — nothing you entered was lost. Please try again in a moment.");
        model.addAttribute("showRetry", true);
        return "error";
    }
}
