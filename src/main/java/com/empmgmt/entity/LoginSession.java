package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One row per login, closed out with a logoutAt timestamp when the user signs out (or left null if the session is still open / the app restarted before logout). */
@Entity
@Table(name = "login_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 20)
    private String role;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Column(name = "logout_at")
    private LocalDateTime logoutAt;
}
