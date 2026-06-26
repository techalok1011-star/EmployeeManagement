package com.empmgmt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "whatsapp")
@Data
public class WhatsAppProperties {

    /**
     * LOG_ONLY  — simulate sending, log message to console/DB, never call Meta API.
     * LIVE      — call Meta Cloud API for real.
     */
    private Mode mode = Mode.LOG_ONLY;

    /** Meta permanent / long-lived access token */
    private String accessToken = "";

    /** WhatsApp Business phone number ID from Meta dashboard */
    private String phoneNumberId = "";

    /** Meta Graph API base URL */
    private String apiUrl = "https://graph.facebook.com/v18.0";

    /** Approved template name registered on Meta Business Manager */
    private String templateName = "payment_reminder";

    /** Template language code */
    private String templateLanguage = "en";

    public enum Mode { LOG_ONLY, LIVE }
}
