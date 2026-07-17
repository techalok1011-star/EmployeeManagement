package com.empmgmt.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AuthController {

    /**
     * Keep-alive target for the external cron (see .github/workflows/keep-alive.yml) that
     * pings the Render free-tier instance so it doesn't idle-sleep. Deliberately does not
     * touch the DB/session - just proves the JVM/servlet container is up, matching what
     * "is this instance awake" actually needs. permitAll in SecurityConfig.
     */
    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            HttpServletRequest request,
                            Model model) {
        // Force the session (and with it, the CSRF token) to exist before the view starts
        // streaming. Without this, a large page can fill Tomcat's response buffer and commit
        // the response before Thymeleaf evaluates ${_csrf.token} — and creating a session after
        // the response is committed throws IllegalStateException. This is most visible right
        // after logout, since logout invalidates the session.
        request.getSession(true);

        if (error != null) {
            model.addAttribute("errorMsg", "Invalid username or password.");
        }
        if (logout != null) {
            model.addAttribute("logoutMsg", "You have been logged out successfully.");
        }
        return "login";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }
}
