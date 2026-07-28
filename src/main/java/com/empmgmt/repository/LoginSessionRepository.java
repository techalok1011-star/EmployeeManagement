package com.empmgmt.repository;

import com.empmgmt.entity.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    List<LoginSession> findTop200ByOrderByLoginAtDesc();
    Optional<LoginSession> findTopByUsernameAndLogoutAtIsNullOrderByLoginAtDesc(String username);
}
