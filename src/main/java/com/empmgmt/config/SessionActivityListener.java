package com.empmgmt.config;

import com.empmgmt.service.LoginSessionService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Closes out the matching {@code login_sessions} row when a session ends other than via the
 * explicit /logout button - most importantly the 30-minute inactivity timeout
 * (server.servlet.session.timeout), which never runs SecurityConfig's logoutSuccessHandler.
 * Also fires for explicit logout (session.invalidate() runs as part of that flow too), but
 * LoginSessionService.recordLogout() is a no-op if that session's already been closed out,
 * so double-firing is harmless.
 */
@Component
@RequiredArgsConstructor
public class SessionActivityListener implements HttpSessionListener {

    private final LoginSessionService loginSessionService;

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        Object attr = event.getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(attr instanceof SecurityContext context)) return;
        if (context.getAuthentication() == null || !context.getAuthentication().isAuthenticated()) return;
        loginSessionService.recordLogout(context.getAuthentication().getName());
    }
}
