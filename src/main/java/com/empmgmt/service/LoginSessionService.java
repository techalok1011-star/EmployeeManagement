package com.empmgmt.service;

import com.empmgmt.entity.LoginSession;
import com.empmgmt.entity.User;
import com.empmgmt.repository.LoginSessionRepository;
import com.empmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tracks login/logout timing for the admin "Login History" report - see SecurityConfig's successHandler/logoutSuccessHandler. */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LoginSessionService {

    // login_at/logout_at are plain LocalDateTime (no zone stored) - must always be captured as
    // IST wall-clock time explicitly, since the JVM's system-default zone is UTC on Render's
    // container (same root cause already hit and fixed for NotificationScheduler's cron).
    // LocalDateTime.now() alone would silently store UTC time there, 5.5h behind actual IST.
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final LoginSessionRepository loginSessionRepository;
    private final UserRepository userRepository;

    /**
     * Every row still open ("logout_at IS NULL") when a fresh JVM boots is guaranteed stale -
     * a brand-new process has zero live HTTP sessions, so none of them can possibly still be
     * real. Without this, a row orphaned by a killed process (Render free-tier spin-down, a
     * redeploy, a local restart) shows "🟢 Active" forever, since neither the 30-minute
     * inactivity timeout nor SessionActivityListener ever gets a chance to run for a process
     * that's already dead.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void closeSessionsOrphanedByRestart() {
        int closed = loginSessionRepository.closeAllOpenSessions(LocalDateTime.now(IST));
        if (closed > 0) {
            log.info("[LoginSessionService] Closed {} login_sessions row(s) left open by the previous process.", closed);
        }
    }

    public void recordLogin(String username, BigDecimal latitude, BigDecimal longitude) {
        User user = userRepository.findByUsername(username).orElse(null);
        loginSessionRepository.save(LoginSession.builder()
                .username(username)
                .fullName(user != null ? user.getFullName() : username)
                .role(user != null ? user.getRole().name() : null)
                .loginAt(LocalDateTime.now(IST))
                .latitude(latitude)
                .longitude(longitude)
                .build());
    }

    public void recordLogout(String username) {
        if (username == null) return;
        loginSessionRepository.findTopByUsernameAndLogoutAtIsNullOrderByLoginAtDesc(username)
                .ifPresent(session -> {
                    session.setLogoutAt(LocalDateTime.now(IST));
                    loginSessionRepository.save(session);
                });
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentSessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LoginSession s : loginSessionRepository.findTop200ByOrderByLoginAtDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", s.getUsername());
            row.put("fullName", s.getFullName());
            row.put("role", s.getRole());
            row.put("loginAt", s.getLoginAt());
            row.put("logoutAt", s.getLogoutAt());
            row.put("latitude", s.getLatitude());
            row.put("longitude", s.getLongitude());
            row.put("active", s.getLogoutAt() == null);
            LocalDateTime end = s.getLogoutAt() != null ? s.getLogoutAt() : LocalDateTime.now(IST);
            row.put("durationText", formatDuration(Duration.between(s.getLoginAt(), end)));
            result.add(row);
        }
        return result;
    }

    private String formatDuration(Duration d) {
        long totalMinutes = Math.max(0, d.toMinutes());
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
