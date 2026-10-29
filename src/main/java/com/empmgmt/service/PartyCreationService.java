package com.empmgmt.service;

import com.empmgmt.dto.PartyDTO;
import com.empmgmt.entity.Party;
import com.empmgmt.event.PartyCreatedEvent;
import com.empmgmt.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The single place a party is created from the UI, so every path audits it, clears the
 * party-derived caches and notifies the admin the same way.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartyCreationService {

    private final PartyRepository partyRepository;
    private final AuditLogService auditLogService;
    private final UserService userService;
    private final InvoiceService invoiceService;
    private final ApplicationEventPublisher eventPublisher;

    /** @throws IllegalArgumentException if a party with the same combined name already exists. */
    @Transactional
    public Party create(PartyDTO.Request request, String actorUsername) {
        String name = request.getName().trim();
        String gstin = request.getGstin() != null ? request.getGstin().trim() : "";
        String combined = gstin.isEmpty() ? name : name + "_" + gstin;
        if (partyRepository.existsByCombined(combined)) {
            throw new IllegalArgumentException("Party '" + combined + "' already exists.");
        }

        Party saved = partyRepository.save(Party.builder()
                .name(name)
                .gst(gstin)
                .combined(combined)
                .trailingNumber(blankToNull(request.getTrailingNumber()))
                .phone(blankToNull(request.getPhone()))
                .whatsappOptIn(request.isWhatsappOptIn())
                .openingBalance(request.getOpeningBalance() != null ? request.getOpeningBalance() : BigDecimal.ZERO)
                .openingBalanceDate(request.getOpeningBalanceDate())
                .build());

        String role = userService.getUserByUsername(actorUsername).getRole();
        auditLogService.log("CREATE", "PARTY", saved.getId(), saved.getCombined(),
                null, "Party '" + saved.getCombined() + "' created", actorUsername, role);
        invoiceService.evictPartyDerivedCaches();
        eventPublisher.publishEvent(new PartyCreatedEvent(saved, actorUsername));
        log.info("Party created: id={} combined='{}' by={}", saved.getId(), saved.getCombined(), actorUsername);
        return saved;
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
