package com.empmgmt.service;

import com.empmgmt.dto.ExecutiveDashboardDTO;
import com.empmgmt.dto.InvoiceDTO;
import com.empmgmt.dto.PartyAgingDTO;
import com.empmgmt.dto.PartyLedgerDTO;
import com.empmgmt.dto.PartyOutstandingDTO;
import com.empmgmt.dto.PartyPaymentBehaviorDTO;
import com.empmgmt.entity.Invoice;
import com.empmgmt.entity.Party;
import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.repository.InvoiceRepository;
import com.empmgmt.repository.PartyRepository;
import com.empmgmt.repository.PaymentEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.empmgmt.config.CacheConfig.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
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
                .deliveryMode(request.getDeliveryMode())
                .transportNumber(request.getTransportNumber() != null && !request.getTransportNumber().isBlank()
                        ? request.getTransportNumber().trim() : null)
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

    /** Invoices within [from, to] inclusive, newest first. Either bound may be null to leave that side open. */
    @Transactional(readOnly = true)
    public List<InvoiceDTO.Response> getInvoicesByDateRange(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.of(1900, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2999, 12, 31);
        return invoiceRepository.findByInvoiceDateBetween(start, end).stream()
                .sorted(Comparator.comparing(Invoice::getInvoiceDate).reversed()
                        .thenComparing(Comparator.comparing(Invoice::getCreatedAt).reversed()))
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceDTO.Response getInvoiceById(Long id) {
        return toResponse(invoiceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + id)));
    }

    // ─────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────

    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
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
    @Cacheable(PARTY_OUTSTANDING)
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
                    .trailingNumber(party != null ? party.getTrailingNumber() : null)
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
    @Cacheable(INVOICE_STATS)
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
    // PARTY LEDGER (Statement of Account)
    // ─────────────────────────────────────────────────────────

    /**
     * Chronological statement for one party: every invoice (debit) and payment
     * (credit) interleaved with a running balance, newest first.
     *
     * @param combinedPartyName Party.combined key (name, or name_gstin)
     */
    @Cacheable(value = PARTY_LEDGER, key = "#combinedPartyName")
    @Transactional(readOnly = true)
    public PartyLedgerDTO.Response getPartyLedger(String combinedPartyName) {
        List<Invoice> invoices = invoiceRepository.findByPartyNameOrderByInvoiceDateDescCreatedAtDesc(combinedPartyName);
        List<PaymentEntry> payments = paymentEntryRepository.findByPartyNameOrderByEntryDateDescCreatedAtDesc(combinedPartyName);

        List<PartyLedgerDTO.Entry> entries = new ArrayList<>();
        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            String deliveryInfo = inv.getDeliveryMode() != null
                    ? inv.getDeliveryMode().getDisplayName()
                        + (inv.getTransportNumber() != null ? " • " + inv.getTransportNumber() : "")
                    : null;
            entries.add(PartyLedgerDTO.Entry.builder()
                    .date(inv.getInvoiceDate())
                    .type("INVOICE")
                    .reference(inv.getInvoiceNumber())
                    .description(inv.getDescription())
                    .deliveryInfo(deliveryInfo)
                    .salesVchNo(inv.getSalesVchNo())
                    .debit(inv.getAmount())
                    .sortTiebreak(inv.getCreatedAt())
                    .build());
            totalInvoiced = totalInvoiced.add(inv.getAmount());
        }
        for (PaymentEntry p : payments) {
            entries.add(PartyLedgerDTO.Entry.builder()
                    .date(p.getEntryDate())
                    .type("PAYMENT")
                    .reference(p.getModeOfPayment().getDisplayName())
                    .description(p.getRemarks())
                    .receiptVchNo(p.getReceiptVchNo())
                    .credit(p.getAmount())
                    .sortTiebreak(p.getCreatedAt())
                    .build());
            totalPaid = totalPaid.add(p.getAmount());
        }

        // Oldest first to compute a correct running balance, then reverse for display.
        entries.sort(Comparator.comparing(PartyLedgerDTO.Entry::getDate)
                .thenComparing(PartyLedgerDTO.Entry::getSortTiebreak, Comparator.nullsFirst(Comparator.naturalOrder())));

        BigDecimal running = BigDecimal.ZERO;
        for (PartyLedgerDTO.Entry e : entries) {
            running = e.getDebit() != null ? running.add(e.getDebit()) : running.subtract(e.getCredit());
            e.setBalance(running);
        }
        Collections.reverse(entries);

        int sep = combinedPartyName.indexOf('_');
        String displayName = sep > 0 ? combinedPartyName.substring(0, sep) : combinedPartyName;
        String gstin = sep > 0 ? combinedPartyName.substring(sep + 1) : "";

        Party party = partyRepository.findByCombined(combinedPartyName).orElse(null);

        return PartyLedgerDTO.Response.builder()
                .partyId(party != null ? party.getId() : null)
                .partyName(combinedPartyName)
                .displayName(displayName)
                .gstin(gstin)
                .trailingNumber(party != null ? party.getTrailingNumber() : null)
                .totalInvoiced(totalInvoiced)
                .totalPaid(totalPaid)
                .outstanding(totalInvoiced.subtract(totalPaid))
                .entries(entries)
                .build();
    }

    /**
     * One statement per party (same order as {@link #getPartyOutstandingSummary()} —
     * outstanding descending), entries oldest-first with a running balance —
     * mirrors the "Party Ledger" sheet layout from the ShivShakti Excel workbook:
     * a block per party, chronological transactions, subtotal, then the next party.
     * Built from two bulk queries (all invoices, all payments) grouped in memory,
     * rather than one query pair per party.
     */
    @Cacheable(ALL_PARTY_LEDGERS)
    @Transactional(readOnly = true)
    public List<PartyLedgerDTO.Response> getAllPartyLedgers() {
        Map<String, List<Invoice>> invoicesByParty = invoiceRepository.findAllByOrderByInvoiceDateDescCreatedAtDesc()
                .stream().collect(Collectors.groupingBy(Invoice::getPartyName));
        Map<String, List<PaymentEntry>> paymentsByParty = paymentEntryRepository.findAllByOrderByCreatedAtDesc()
                .stream().collect(Collectors.groupingBy(PaymentEntry::getPartyName));

        List<PartyLedgerDTO.Response> result = new ArrayList<>();
        for (PartyOutstandingDTO summary : getPartyOutstandingSummary()) {
            String combined = summary.getPartyName();
            List<Invoice> invoices = invoicesByParty.getOrDefault(combined, List.of());
            List<PaymentEntry> payments = paymentsByParty.getOrDefault(combined, List.of());

            List<PartyLedgerDTO.Entry> entries = new ArrayList<>();
            for (Invoice inv : invoices) {
                String deliveryInfo = inv.getDeliveryMode() != null
                        ? inv.getDeliveryMode().getDisplayName()
                            + (inv.getTransportNumber() != null ? " • " + inv.getTransportNumber() : "")
                        : null;
                entries.add(PartyLedgerDTO.Entry.builder()
                        .date(inv.getInvoiceDate())
                        .type("INVOICE")
                        .reference(inv.getInvoiceNumber())
                        .description(inv.getDescription())
                        .deliveryInfo(deliveryInfo)
                        .salesVchNo(inv.getSalesVchNo())
                        .debit(inv.getAmount())
                        .sortTiebreak(inv.getCreatedAt())
                        .build());
            }
            for (PaymentEntry p : payments) {
                entries.add(PartyLedgerDTO.Entry.builder()
                        .date(p.getEntryDate())
                        .type("PAYMENT")
                        .reference(p.getModeOfPayment().getDisplayName())
                        .description(p.getRemarks())
                        .receiptVchNo(p.getReceiptVchNo())
                        .credit(p.getAmount())
                        .sortTiebreak(p.getCreatedAt())
                        .build());
            }

            // Oldest first, like the Excel sheet — no reversal (unlike the single-party ledger page).
            entries.sort(Comparator.comparing(PartyLedgerDTO.Entry::getDate)
                    .thenComparing(PartyLedgerDTO.Entry::getSortTiebreak, Comparator.nullsFirst(Comparator.naturalOrder())));

            BigDecimal running = BigDecimal.ZERO;
            for (PartyLedgerDTO.Entry e : entries) {
                running = e.getDebit() != null ? running.add(e.getDebit()) : running.subtract(e.getCredit());
                e.setBalance(running);
            }

            result.add(PartyLedgerDTO.Response.builder()
                    .partyId(summary.getPartyId())
                    .partyName(combined)
                    .displayName(summary.getDisplayName())
                    .gstin(summary.getGstin())
                    .trailingNumber(summary.getTrailingNumber())
                    .totalInvoiced(summary.getTotalInvoiced())
                    .totalPaid(summary.getTotalPaid())
                    .outstanding(summary.getOutstanding())
                    .entries(entries)
                    .build());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // AGING REPORT
    // ─────────────────────────────────────────────────────────

    /**
     * One row per party with outstanding &gt; 0, bucketed by how old its oldest
     * unpaid invoice is. Payments are allocated FIFO against invoices (oldest
     * invoice first) to determine which invoice(s) remain outstanding — there's
     * no per-invoice payment link in the schema, so this is the standard
     * approximation used for aging without one.
     */
    @Cacheable(AGING_REPORT)
    @Transactional(readOnly = true)
    public List<PartyAgingDTO> getAgingReport() {
        LocalDate today = LocalDate.now();
        List<PartyAgingDTO> result = new ArrayList<>();

        for (PartyOutstandingDTO summary : getPartyOutstandingSummary()) {
            if (summary.getOutstanding() == null || summary.getOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<Invoice> invoices = invoiceRepository.findByPartyNameOrderByInvoiceDateDescCreatedAtDesc(summary.getPartyName());
            invoices.sort(Comparator.comparing(Invoice::getInvoiceDate));

            BigDecimal paidRemaining = summary.getTotalPaid() != null ? summary.getTotalPaid() : BigDecimal.ZERO;
            LocalDate oldestUnpaidDate = null;
            for (Invoice inv : invoices) {
                if (paidRemaining.compareTo(inv.getAmount()) >= 0) {
                    paidRemaining = paidRemaining.subtract(inv.getAmount());
                } else {
                    oldestUnpaidDate = inv.getInvoiceDate();
                    break;
                }
            }

            long daysOverdue = oldestUnpaidDate != null ? ChronoUnit.DAYS.between(oldestUnpaidDate, today) : 0;
            String bucket = daysOverdue <= 30 ? "0-30" : daysOverdue <= 60 ? "31-60" : daysOverdue <= 90 ? "61-90" : "90+";

            result.add(PartyAgingDTO.builder()
                    .partyId(summary.getPartyId())
                    .partyName(summary.getPartyName())
                    .displayName(summary.getDisplayName())
                    .gstin(summary.getGstin())
                    .outstanding(summary.getOutstanding())
                    .oldestUnpaidInvoiceDate(oldestUnpaidDate)
                    .daysOverdue(daysOverdue)
                    .bucket(bucket)
                    .phone(summary.getPhone())
                    .whatsappOptIn(summary.isWhatsappOptIn())
                    .build());
        }

        result.sort(Comparator.comparingLong(PartyAgingDTO::getDaysOverdue).reversed());
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // PARTY PAYMENT BEHAVIOR
    // ─────────────────────────────────────────────────────────

    /**
     * One row per party: current standing (reused from {@link #getAgingReport()}'s
     * bucket logic) plus payment history. The primary behavior label is driven by
     * the current aging bucket + payment recency rather than "days to fully settle
     * an invoice" — most parties here run a rolling balance (partial ongoing
     * payments against a growing tab) rather than clearing exact invoices one at a
     * time, so a FIFO full-settlement metric alone rarely fires and would
     * misrepresent an actively-paying party as having "no history." The
     * FIFO-derived average is still computed and shown as supplementary context
     * when it is available. Sorted worst-standing-first so chronic late payers
     * surface immediately.
     */
    @Cacheable(PAYMENT_BEHAVIOR)
    @Transactional(readOnly = true)
    public List<PartyPaymentBehaviorDTO> getPartyPaymentBehavior() {
        LocalDate today = LocalDate.now();
        List<PartyPaymentBehaviorDTO> result = new ArrayList<>();

        for (PartyOutstandingDTO summary : getPartyOutstandingSummary()) {
            List<Invoice> invoices = invoiceRepository.findByPartyNameOrderByInvoiceDateDescCreatedAtDesc(summary.getPartyName());
            invoices.sort(Comparator.comparing(Invoice::getInvoiceDate));
            List<PaymentEntry> payments = paymentEntryRepository.findByPartyNameOrderByEntryDateDescCreatedAtDesc(summary.getPartyName());
            payments.sort(Comparator.comparing(PaymentEntry::getEntryDate));

            int paymentIdx = 0;
            BigDecimal paymentRemaining = payments.isEmpty() ? BigDecimal.ZERO : payments.get(0).getAmount();
            long totalDelayDays = 0;
            long paidInvoiceCount = 0;
            LocalDate oldestUnpaidDate = null;

            for (Invoice inv : invoices) {
                BigDecimal remaining = inv.getAmount();
                LocalDate lastPaymentDateUsed = null;
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && paymentIdx < payments.size()) {
                    if (paymentRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                        paymentIdx++;
                        if (paymentIdx >= payments.size()) break;
                        paymentRemaining = payments.get(paymentIdx).getAmount();
                    }
                    BigDecimal consume = remaining.min(paymentRemaining);
                    remaining = remaining.subtract(consume);
                    paymentRemaining = paymentRemaining.subtract(consume);
                    lastPaymentDateUsed = payments.get(paymentIdx).getEntryDate();
                }
                if (remaining.compareTo(BigDecimal.ZERO) <= 0 && lastPaymentDateUsed != null) {
                    totalDelayDays += Math.max(0, ChronoUnit.DAYS.between(inv.getInvoiceDate(), lastPaymentDateUsed));
                    paidInvoiceCount++;
                } else {
                    oldestUnpaidDate = inv.getInvoiceDate();
                    break;
                }
            }

            Double avgDaysToPay = paidInvoiceCount > 0 ? (double) totalDelayDays / paidInvoiceCount : null;

            BigDecimal outstanding = summary.getOutstanding() != null ? summary.getOutstanding() : BigDecimal.ZERO;
            String bucket;
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                bucket = summary.isCredit() ? "Credit" : "Clear";
            } else {
                long daysOverdue = oldestUnpaidDate != null ? ChronoUnit.DAYS.between(oldestUnpaidDate, today) : 0;
                bucket = daysOverdue <= 30 ? "0-30" : daysOverdue <= 60 ? "31-60" : daysOverdue <= 90 ? "61-90" : "90+";
            }

            String label;
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                label = "Paid Up";
            } else {
                switch (bucket) {
                    case "0-30": label = payments.isEmpty() ? "New" : "Regular"; break;
                    case "31-60": label = "Slow"; break;
                    case "61-90": label = "Very Slow"; break;
                    default: label = "Chronic Late";
                }
            }

            boolean eligible = outstanding.compareTo(BigDecimal.ZERO) > 0
                    && summary.getPhone() != null && !summary.getPhone().isBlank()
                    && summary.isWhatsappOptIn();

            result.add(PartyPaymentBehaviorDTO.builder()
                    .partyId(summary.getPartyId())
                    .partyName(summary.getPartyName())
                    .displayName(summary.getDisplayName())
                    .gstin(summary.getGstin())
                    .outstanding(outstanding)
                    .currentBucket(bucket)
                    .avgDaysToPay(avgDaysToPay)
                    .paidInvoiceCount(paidInvoiceCount)
                    .totalPaymentsCount(payments.size())
                    .lastPaymentDate(payments.isEmpty() ? null : payments.get(payments.size() - 1).getEntryDate())
                    .behaviorLabel(label)
                    .phone(summary.getPhone())
                    .whatsappOptIn(summary.isWhatsappOptIn())
                    .eligibleForReminder(eligible)
                    .build());
        }

        result.sort(Comparator
                .comparingInt((PartyPaymentBehaviorDTO b) -> behaviorSeverity(b.getBehaviorLabel()))
                .reversed()
                .thenComparing(Comparator.comparing(PartyPaymentBehaviorDTO::getOutstanding).reversed()));
        return result;
    }

    /** Higher = more concerning, for sorting the Payment Behavior table worst-first. */
    private int behaviorSeverity(String label) {
        switch (label) {
            case "Chronic Late": return 4;
            case "Very Slow": return 3;
            case "Slow": return 2;
            case "New": return 1;
            case "Regular": return 0;
            default: return -1; // Paid Up
        }
    }

    // ─────────────────────────────────────────────────────────
    // EXECUTIVE DASHBOARD
    // ─────────────────────────────────────────────────────────

    /**
     * High-level owner/executive summary: totals, top defaulters, and a
     * 6-month invoiced-vs-collected trend.
     */
    @Transactional(readOnly = true)
    public ExecutiveDashboardDTO getExecutiveSummary() {
        BigDecimal totalInvoiced = invoiceRepository.sumAllAmounts();
        BigDecimal totalCollected = paymentEntryRepository.sumAllAmounts();
        BigDecimal totalOutstanding = totalInvoiced.subtract(totalCollected);

        LocalDate today = LocalDate.now();
        BigDecimal todayCollections = paymentEntryRepository.sumAmountByDate(today);
        if (todayCollections == null) {
            todayCollections = BigDecimal.ZERO;
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal monthCollections = paymentEntryRepository.findAllByDateRange(monthStart, today).stream()
                .map(PaymentEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PartyOutstandingDTO> topDefaulters = getPartyOutstandingSummary().stream()
                .filter(p -> p.getOutstanding() != null && p.getOutstanding().compareTo(BigDecimal.ZERO) > 0)
                .limit(10)
                .collect(Collectors.toList());

        // Last 6 months trend (oldest first)
        LocalDate windowStart = YearMonth.from(today).minusMonths(5).atDay(1);
        List<PaymentEntry> paymentsWindow = paymentEntryRepository.findAllByDateRange(windowStart, today);
        List<Invoice> invoicesWindow = invoiceRepository.findByInvoiceDateBetween(windowStart, today);

        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");
        List<String> monthlyLabels = new ArrayList<>();
        List<BigDecimal> monthlyInvoiced = new ArrayList<>();
        List<BigDecimal> monthlyCollected = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.from(today).minusMonths(i);
            monthlyLabels.add(ym.atDay(1).format(monthFmt));
            monthlyCollected.add(paymentsWindow.stream()
                    .filter(p -> YearMonth.from(p.getEntryDate()).equals(ym))
                    .map(PaymentEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            monthlyInvoiced.add(invoicesWindow.stream()
                    .filter(inv -> YearMonth.from(inv.getInvoiceDate()).equals(ym))
                    .map(Invoice::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        return ExecutiveDashboardDTO.builder()
                .totalInvoiced(totalInvoiced)
                .totalCollected(totalCollected)
                .totalOutstanding(totalOutstanding)
                .todayCollections(todayCollections)
                .monthCollections(monthCollections)
                .topDefaulters(topDefaulters)
                .monthlyLabels(monthlyLabels)
                .monthlyInvoiced(monthlyInvoiced)
                .monthlyCollected(monthlyCollected)
                .build();
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
                .deliveryMode(inv.getDeliveryMode() != null ? inv.getDeliveryMode().getDisplayName() : "")
                .transportNumber(inv.getTransportNumber())
                .salesVchNo(inv.getSalesVchNo())
                .createdAt(inv.getCreatedAt() != null ? inv.getCreatedAt().format(FMT) : "")
                .build();
    }
}
