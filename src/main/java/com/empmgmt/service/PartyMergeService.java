package com.empmgmt.service;

import com.empmgmt.dto.PartyMergeDTO;
import com.empmgmt.entity.Party;
import com.empmgmt.repository.InvoiceRepository;
import com.empmgmt.repository.PartyRepository;
import com.empmgmt.repository.PaymentEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.empmgmt.config.CacheConfig.*;

/**
 * Fixes the "same real party split across multiple party_name text strings" problem
 * documented repeatedly in project history (PRASANG/SURAJ/USMANI) - invoices/payment_entries
 * store party_name as free text with no FK to parties, so a typo, a GST-suffix edit, or a
 * renamed parties row all silently fork a party's history in two. Previously fixed by hand,
 * one party at a time, via direct SQL plus the entry-edit UI. This re-points every invoice
 * and payment from one party_name onto another in a single transaction, with one consolidated
 * audit_logs row rather than N individual entry edits.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PartyMergeService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentEntryRepository paymentEntryRepository;
    private final PartyRepository partyRepository;
    private final AuditLogService auditLogService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<PartyMergeDTO.OrphanRow> getOrphanedParties() {
        return partyRepository.findOrphanedPartyNames().stream()
                .map(row -> {
                    String partyName = (String) row[0];
                    long invoiceCount = ((Number) row[1]).longValue();
                    BigDecimal invoiceAmount = (BigDecimal) row[2];
                    long paymentCount = ((Number) row[3]).longValue();
                    BigDecimal paymentAmount = (BigDecimal) row[4];
                    return PartyMergeDTO.OrphanRow.builder()
                            .partyName(partyName)
                            .invoiceCount(invoiceCount)
                            .invoiceAmount(invoiceAmount)
                            .paymentCount(paymentCount)
                            .paymentAmount(paymentAmount)
                            .totalAmount(invoiceAmount.add(paymentAmount))
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PartyMergeDTO.Preview preview(String fromName, String toCombined) {
        PartyMergeDTO.Preview.PreviewBuilder builder = PartyMergeDTO.Preview.builder()
                .fromName(fromName)
                .fromHasPartyRecord(fromName != null && partyRepository.existsByCombined(fromName))
                .fromInvoiceCount(fromName == null ? 0 : invoiceRepository.countByPartyName(fromName))
                .fromInvoiceAmount(fromName == null ? BigDecimal.ZERO : invoiceRepository.sumAmountByPartyName(fromName))
                .fromPaymentCount(fromName == null ? 0 : paymentEntryRepository.countByPartyName(fromName))
                .fromPaymentAmount(fromName == null ? BigDecimal.ZERO : paymentEntryRepository.sumAmountByPartyName(fromName));

        if (toCombined != null && !toCombined.isBlank()) {
            var target = partyRepository.findByCombined(toCombined);
            builder.toCombined(toCombined)
                    .toExists(target.isPresent())
                    .toPhone(target.map(Party::getPhone).orElse(null))
                    .toWhatsappOptIn(target.map(Party::isWhatsappOptIn).orElse(false))
                    .toInvoiceCount(invoiceRepository.countByPartyName(toCombined))
                    .toInvoiceAmount(invoiceRepository.sumAmountByPartyName(toCombined))
                    .toPaymentCount(paymentEntryRepository.countByPartyName(toCombined))
                    .toPaymentAmount(paymentEntryRepository.sumAmountByPartyName(toCombined));
        }
        return builder.build();
    }

    /**
     * Re-points every invoice/payment from fromName onto toCombined. toCombined must already be a
     * live parties.combined row - merging into a name with no Party record would just create a new
     * orphan, and the app's own "no live Party record" filter (see InvoiceService, commit bbeb559)
     * would then drop the merged history from every ledger/report/reminder. transaction_logs is
     * deliberately left untouched: it's an immutable point-in-time audit snapshot, not live data.
     */
    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
    public PartyMergeDTO.Result merge(PartyMergeDTO.Request request, String performedBy) {
        String fromName = request.getFromName().trim();
        String toCombined = request.getToCombined().trim();

        if (fromName.equals(toCombined)) {
            throw new IllegalArgumentException("'From' and 'To' are the same party - nothing to merge.");
        }

        Party target = partyRepository.findByCombined(toCombined)
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + toCombined + "' has no Party record. Merges must target an existing party " +
                        "(add it under Parties first if it's genuinely new) - otherwise the merged history " +
                        "would just become a new orphan, invisible to ledgers/reports/reminders."));

        long invoiceCount = invoiceRepository.countByPartyName(fromName);
        long paymentCount = paymentEntryRepository.countByPartyName(fromName);
        if (invoiceCount == 0 && paymentCount == 0) {
            throw new IllegalArgumentException(
                    "No invoices or payments are filed under '" + fromName + "' - nothing to merge.");
        }
        BigDecimal invoiceAmount = invoiceRepository.sumAmountByPartyName(fromName);
        BigDecimal paymentAmount = paymentEntryRepository.sumAmountByPartyName(fromName);

        invoiceRepository.repointPartyName(fromName, toCombined);
        paymentEntryRepository.repointPartyName(fromName, toCombined);

        String description = String.format(
                "Merged party '%s' into '%s': %d invoice(s) (₹%s) + %d payment(s) (₹%s) re-pointed. Reason: %s",
                fromName, toCombined, invoiceCount, invoiceAmount.toPlainString(),
                paymentCount, paymentAmount.toPlainString(), request.getRemarks());
        String role = userService.getUserByUsername(performedBy).getRole();
        auditLogService.log("MERGE", "PARTY", target.getId(), toCombined,
                invoiceAmount.add(paymentAmount), description, performedBy, role);
        log.info("Party merge by {}: '{}' -> '{}' ({} invoices, {} payments)",
                performedBy, fromName, toCombined, invoiceCount, paymentCount);

        return PartyMergeDTO.Result.builder()
                .fromName(fromName)
                .toCombined(toCombined)
                .invoicesMoved(invoiceCount)
                .invoiceAmount(invoiceAmount)
                .paymentsMoved(paymentCount)
                .paymentAmount(paymentAmount)
                .build();
    }
}
