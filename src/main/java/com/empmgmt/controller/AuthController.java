package com.empmgmt.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

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
