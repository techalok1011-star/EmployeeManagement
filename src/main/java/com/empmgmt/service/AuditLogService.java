package com.empmgmt.service;

import com.empmgmt.dto.AuditFeedDTO;
import com.empmgmt.entity.AuditLog;
import com.empmgmt.entity.TransactionLog;
import com.empmgmt.repository.AuditLogRepository;
import com.empmgmt.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Records and serves the admin-facing audit trail for actions that aren't a
 * PaymentEntry (those already have their own trail - see PaymentEntryService's
 * logAction()/TransactionLog, left untouched). {@link #getUnifiedFeed()} merges
 * both sources into one chronological view for the Audit Log page.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final TransactionLogRepository transactionLogRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm:ss a");

    public void log(String action, String entityType, Long entityId, String partyName,
                     BigDecimal amount, String description, String performedBy, String performedByRole) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .partyName(partyName)
                .amount(amount)
                .description(description)
                .performedBy(performedBy)
                .performedByRole(performedByRole)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditFeedDTO.Response> getUnifiedFeed() {
        List<AuditFeedDTO.Response> feed = new ArrayList<>();

        for (TransactionLog t : transactionLogRepository.findAllByOrderByPerformedAtDesc()) {
            feed.add(AuditFeedDTO.Response.builder()
                    .category("Payment Entry")
                    .action(t.getAction())
                    .employeeName(t.getEmployeeName())
                    .employeeUsername(t.getEmployeeUsername())
                    .partyName(t.getPartyName())
                    .amount(t.getAmount())
                    .modeOfPayment(t.getModeOfPayment())
                    .entryDate(t.getEntryDate())
                    .performedBy(t.getPerformedBy())
                    .performedByRole(null) // not captured historically for payment entries
                    .notes(t.getNotes())
                    .performedAtRaw(t.getPerformedAt())
                    .performedAtDisplay(t.getPerformedAt() != null ? t.getPerformedAt().format(FMT) : "")
                    .build());
        }

        for (AuditLog a : auditLogRepository.findAllByOrderByPerformedAtDesc()) {
            feed.add(AuditFeedDTO.Response.builder()
                    .category("INVOICE".equals(a.getEntityType()) ? "Invoice" : "Party")
                    .action(a.getAction())
                    .partyName(a.getPartyName())
                    .amount(a.getAmount())
                    .performedBy(a.getPerformedBy())
                    .performedByRole(a.getPerformedByRole())
                    .notes(a.getDescription())
                    .performedAtRaw(a.getPerformedAt())
                    .performedAtDisplay(a.getPerformedAt() != null ? a.getPerformedAt().format(FMT) : "")
                    .build());
        }

        feed.sort(Comparator.comparing(AuditFeedDTO.Response::getPerformedAtRaw,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return feed;
    }
}
