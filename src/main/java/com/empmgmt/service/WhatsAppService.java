package com.empmgmt.service;

import com.empmgmt.config.WhatsAppProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sends WhatsApp messages via Meta Business Cloud API.
 *
 * Set whatsapp.mode=LOG_ONLY (default) to simulate without real API calls.
 * Set whatsapp.mode=LIVE and supply access-token + phone-number-id for production.
 *
 * Template (register on Meta Business Manager with name = whatsapp.template-name):
 *   "Dear {{1}}, your outstanding amount with PayTrack is ₹{{2}} as of {{3}}.
 *    Please arrange payment at your earliest convenience. Thank you."
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final WhatsAppProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Data
    public static class SendResult {
        private final boolean success;
        private final boolean dryRun;
        private final String error;

        public static SendResult ok()         { return new SendResult(true, false, null); }
        public static SendResult dryRun()     { return new SendResult(true, true,  null); }
        public static SendResult failed(String e) { return new SendResult(false, false, e); }
    }

    /**
     * @param phone         E.164 format without '+', e.g. "919876543210"
     * @param partyDisplay  Human-readable party name
     * @param outstanding   Outstanding amount (always > 0 when called)
     * @param asOfDate      Date label for the message
     */
    public SendResult sendPaymentReminder(String phone, String partyDisplay,
                                          BigDecimal outstanding, LocalDate asOfDate) {
        String amountStr = NumberFormat.getNumberInstance(new Locale("en", "IN"))
                .format(outstanding) + ".00";
        String dateStr   = asOfDate.format(DATE_FMT);

        if (props.getMode() == WhatsAppProperties.Mode.LOG_ONLY) {
            log.info("[WhatsApp DRY-RUN] → {} | party={} | outstanding=₹{} | date={}",
                    phone, partyDisplay, amountStr, dateStr);
            return SendResult.dryRun();
        }

        // ── Build Meta Cloud API request ──
        String url = props.getApiUrl() + "/" + props.getPhoneNumberId() + "/messages";

//        Map<String, Object> body = Map.of(
//            "messaging_product", "whatsapp",
//            "to", phone,
//            "type", "template",
//            "template", Map.of(
//                "name", props.getTemplateName(),
//                "language", Map.of("code", props.getTemplateLanguage()),
//                "components", List.of(Map.of(
//                    "type", "body",
//                    "parameters", List.of(
//                        Map.of("type", "text", "text", partyDisplay),
//                        Map.of("type", "text", "text", amountStr),
//                        Map.of("type", "text", "text", dateStr)
//                    )
//                ))
//            )
//        );

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", phone,
                "type", "template",
                "template", Map.of(
                        "name", props.getTemplateName(),
                        "language", Map.of(
                                "code", props.getTemplateLanguage()
                        )
                )
        );

        log.info("Access Token Present: {}", props.getAccessToken() != null);
        log.info("Access Token Length: {}",
                props.getAccessToken() == null ? 0 : props.getAccessToken().length());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getAccessToken());

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[WhatsApp SENT] → {} | party={} | outstanding=₹{}", phone, partyDisplay, amountStr);
                return SendResult.ok();
            } else {
                String err = "HTTP " + response.getStatusCode() + ": " + response.getBody();
                log.error("[WhatsApp FAILED] → {} | {}", phone, err);
                return SendResult.failed(err);
            }
        } catch (Exception ex) {
            log.error("[WhatsApp ERROR] → {} | {}", phone, ex.getMessage());
            return SendResult.failed(ex.getMessage());
        }
    }
}
