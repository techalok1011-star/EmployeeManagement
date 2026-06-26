package com.empmgmt.service;

import com.empmgmt.dto.InvoiceDTO;
import com.empmgmt.dto.PartyOutstandingDTO;
import com.empmgmt.entity.Invoice;
import com.empmgmt.entity.Party;
import com.empmgmt.repository.InvoiceRepository;
import com.empmgmt.repository.PartyRepository;
import com.empmgmt.repository.PaymentEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentEntryRepository paymentEntryRepository;
    private final PartyRepository partyRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    // ─────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────

    public InvoiceDTO.Response createInvoice(InvoiceDTO.Request request) {
        if (invoiceRepository.existsByInvoiceNumber(request.getInvoiceNumber().trim())) {
            throw new IllegalArgumentException(
                    "Invoice number '" + request.getInvoiceNumber() + "' already exists.");
        }
        Invoice saved = invoiceRepository.save(Invoice.builder()
                .invoiceNumber(request.getInvoiceNumber().trim())
                .invoiceDate(request.getInvoiceDate())
                .partyName(request.getPartyName())
                .amount(request.getAmount())
                .description(request.getDescription())
                .build());
        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InvoiceDTO.Response> getAllInvoices() {
        return invoiceRepository.findAllByOrderByInvoiceDateDescCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceDTO.Response getInvoiceById(Long id) {
        return toResponse(invoiceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + id)));
    }

    // ─────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────

    public void deleteInvoice(Long id) {
        if (!invoiceRepository.existsById(id)) {
            throw new NoSuchElementException("Invoice not found: " + id);
        }
        invoiceRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────
    // OUTSTANDING SUMMARY
    // ─────────────────────────────────────────────────────────

    /**
     * Returns one row per party that appears in invoices OR payments, sorted
     * by outstanding amount descending (largest debt first).
     */
    @Transactional(readOnly = true)
    public List<PartyOutstandingDTO> getPartyOutstandingSummary() {

        // 1. Invoice totals per party
        Map<String, BigDecimal> invoicedMap = new LinkedHashMap<>();
        for (Object[] row : invoiceRepository.sumAmountGroupedByPartyName()) {
            invoicedMap.put((String) row[0], (BigDecimal) row[1]);
        }

        // 2. Payment totals per party
        Map<String, BigDecimal> paidMap = new LinkedHashMap<>();
        for (Object[] row : paymentEntryRepository.sumAmountGroupedByPartyName()) {
            paidMap.put((String) row[0], (BigDecimal) row[1]);
        }

        // 3. Union of all party keys
        Set<String> allParties = new LinkedHashSet<>();
        allParties.addAll(invoicedMap.keySet());
        allParties.addAll(paidMap.keySet());

        // 4. Fetch Party records for phone / opt-in data
        Map<String, Party> partyLookup = partyRepository.findByCombinedIn(allParties)
                .stream().collect(Collectors.toMap(Party::getCombined, p -> p));

        return allParties.stream().map(combined -> {
            BigDecimal invoiced    = invoicedMap.getOrDefault(combined, BigDecimal.ZERO);
            BigDecimal paid        = paidMap.getOrDefault(combined, BigDecimal.ZERO);
            BigDecimal outstanding = invoiced.subtract(paid);

            // split "Name_GSTIN" → displayName + gstin
            int sep = combined.indexOf('_');
            String displayName = sep > 0 ? combined.substring(0, sep) : combined;
            String gstin       = sep > 0 ? combined.substring(sep + 1) : "";

            Party party = partyLookup.get(combined);

            return PartyOutstandingDTO.builder()
                    .partyId(party != null ? party.getId() : null)
                    .partyName(combined)
                    .displayName(displayName)
                    .gstin(gstin)
                    .totalInvoiced(invoiced)
                    .totalPaid(paid)
                    .outstanding(outstanding)
                    .phone(party != null ? party.getPhone() : null)
                    .whatsappOptIn(party != null && party.isWhatsappOptIn())
                    .build();
        })
        .sorted(Comparator.comparing(PartyOutstandingDTO::getOutstanding).reversed())
        .collect(Collectors.toList());
    }

    /**
     * Aggregate stats for the invoices page header bar.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInvoicePageStats() {
        BigDecimal totalInvoiced = invoiceRepository.sumAllAmounts();
        BigDecimal totalPaid     = paymentEntryRepository.sumAllAmounts();
        BigDecimal totalOutstanding = totalInvoiced.subtract(totalPaid);
        long invoiceCount = invoiceRepository.count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalInvoiced", totalInvoiced);
        stats.put("totalPaid", totalPaid);
        stats.put("totalOutstanding", totalOutstanding);
        stats.put("invoiceCount", invoiceCount);
        return stats;
    }

    // ─────────────────────────────────────────────────────────
    // MAPPING
    // ─────────────────────────────────────────────────────────

    private InvoiceDTO.Response toResponse(Invoice inv) {
        return InvoiceDTO.Response.builder()
                .id(inv.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .invoiceDate(inv.getInvoiceDate())
                .partyName(inv.getPartyName())
                .amount(inv.getAmount())
                .description(inv.getDescription())
                .createdAt(inv.getCreatedAt() != null ? inv.getCreatedAt().format(FMT) : "")
                .build();
    }
}
