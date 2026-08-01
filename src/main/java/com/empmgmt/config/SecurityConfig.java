package com.empmgmt.config;

import com.empmgmt.security.CustomUserDetailsService;
import com.empmgmt.service.LoginSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.math.BigDecimal;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final LoginSessionService loginSessionService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** Tracks every user's live session(s) so an admin can force-expire one from /admin/login-history. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Required for SessionRegistry to actually get notified when a session is created/destroyed. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // Plain path match, independent of query string — formLogin()/logout()'s own
                // permitAll() only auto-permits the *exact* configured URL strings (e.g.
                // "/login?error=true"), not "/login" with any other query string. Without this,
                // "/login?logout=true" and "/login?expired=true" (the actual redirect targets
                // used by the logout handler, concurrent-session expiry, and the CSRF handler
                // below) silently required authentication and bounced through an extra
                // redirect to a bare, message-less /login.
                .requestMatchers("/login").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/h2-console/**",
                        "/manifest.json", "/manager-manifest.json", "/sw.js", "/icons/**", "/health").permitAll()
                .requestMatchers("/api/parties/suggest", "/api/parties").authenticated()
                .requestMatchers("/api/parties/import", "/api/parties/cleanup", "/api/parties/upload-import").hasAnyRole("ADMIN", "ACCOUNTANT")
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                .requestMatchers("/employee/**").hasRole("EMPLOYEE")
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler((req, res, auth) -> {
                    var authorities = auth.getAuthorities();
                    String redirect;
                    if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                        redirect = "/admin/dashboard";
                    } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ACCOUNTANT"))) {
                        redirect = "/admin/invoices";
                    } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"))) {
                        redirect = "/manager/dashboard";
                    } else {
                        redirect = "/employee/dashboard";
                    }
                    loginSessionService.recordLogin(auth.getName(),
                            parseCoordinate(req.getParameter("latitude")),
                            parseCoordinate(req.getParameter("longitude")));
                    res.sendRedirect(redirect);
                })
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(-1) // unlimited concurrent logins per user - just tracked, not restricted
                .sessionRegistry(sessionRegistry())
                .expiredUrl("/login?expired=true")
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessHandler((req, res, authentication) -> {
                    if (authentication != null) {
                        loginSessionService.recordLogout(authentication.getName());
                    }
                    res.sendRedirect("/login?logout=true");
                })
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**", "/api/parties/import", "/api/parties/cleanup")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            .exceptionHandling(ex -> ex
                // A mobile browser can restore a killed installed-app page from its
                // back/forward cache with a CSRF token baked in from hours earlier; if the
                // session has since expired, that token no longer validates. Rather than
                // show a bare 403 Whitelabel page (confusing, and indistinguishable from a
                // real problem), treat any CSRF rejection as "just sign in again."
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (accessDeniedException instanceof org.springframework.security.web.csrf.CsrfException) {
                        response.sendRedirect(request.getContextPath() + "/login?expired=true");
                    } else {
                        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                    }
                })
            );

        return http.build();
    }

    // Login form's optional geolocation fields — blank/missing (permission denied,
    // unsupported browser) or malformed input should never break login, just omit the location.
    private static BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
