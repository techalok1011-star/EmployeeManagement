package com.empmgmt.repository;

import com.empmgmt.entity.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    List<LoginSession> findTop200ByOrderByLoginAtDesc();
    Optional<LoginSession> findTopByUsernameAndLogoutAtIsNullOrderByLoginAtDesc(String username);

    /**
     * Closes out every still-open row at once. Used at boot to reconcile rows left open by a
     * JVM that died (Render free-tier spin-down, a redeploy, a local restart) before the
     * 30-minute inactivity timeout or SessionActivityListener ever got a chance to run - a
     * fresh JVM has no live sessions at all, so every such row is guaranteed stale.
     */
    @Modifying
    @Query("UPDATE LoginSession l SET l.logoutAt = :closedAt WHERE l.logoutAt IS NULL")
    int closeAllOpenSessions(LocalDateTime closedAt);
}
