package com.empmgmt.service;

import com.empmgmt.dto.VoucherAuditDTO;
import com.empmgmt.entity.VoucherAcknowledgement;
import com.empmgmt.repository.InvoiceRepository;
import com.empmgmt.repository.PaymentEntryRepository;
import com.empmgmt.repository.VoucherAcknowledgementRepository;
import com.empmgmt.util.IstClock;
import com.empmgmt.util.VoucherNumberParser;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.empmgmt.config.CacheConfig.VOUCHER_AUDIT;

/**
 * Finds gaps, duplicates, and mis-keyed numbers in the receipt (payment_entries.receipt_vch_no)
 * and sales (invoices.sales_vch_no) voucher series, scoped to the current financial year.
 * <p>
 * Numbering restarts every FY and the two series are independent (a Tally habit), so mixing years
 * or series would make a genuine gap look like noise - everything here is computed per series,
 * per FY. Parsing happens at query time rather than against a maintained numeric column: at
 * current volume (~2,300 receipts/year) there's no case for keeping a derived column in sync on
 * every write.
 * <p>
 * Three deliberate design choices, driven by real production data (Aug 2026) rather than assumed
 * up front - documented here so they don't look like bugs later:
 * <ul>
 *   <li><b>Not-yet-used vs missing.</b> Numbers above the "high-water mark" (the current diary
 *       book's blank tail) are never reported missing - only numbers below it that were skipped.</li>
 *   <li><b>Suspect jumps freeze the high-water mark.</b> A number far ahead of everything else
 *       (a mis-keyed voucher - "12000000" typed for "1200") is flagged SUSPECT and excluded from
 *       range calculations, so one typo can't manufacture thousands of phantom gaps between it and
 *       the real range. Once a suspect appears, every larger number for the rest of that series is
 *       also suspect relative to the same frozen mark - correct for the isolated-typo pattern seen
 *       so far, but worth revisiting if a future year's data shows a genuine large resumption
 *       (business paused for months, then a dense run resumes far above the old mark).</li>
 *   <li><b>Ambiguous text is never guessed at.</b> A value like "25o2" (letter O for a zero) has a
 *       digit both before and after the substitution point - extracting just the leading "25" would
 *       silently invent a wrong voucher number, so it's surfaced as AMBIGUOUS for a human to resolve
 *       against the paper record instead. Trailing junk with no embedded digit ("2463 T/F", "2010.")
 *       is safe to extract as-is.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VoucherAuditService {

    private static final int BOOK_SIZE = 100;
    private static final long SUSPECT_GAP = 100;
    /** A typo in the range fields (e.g. a stray extra digit) shouldn't be able to acknowledge
     *  half a financial year's worth of numbers in one click. */
    private static final long MAX_ACKNOWLEDGE_RANGE = 10_000;
    private static final Set<String> REASON_CATEGORIES = Set.of("VOIDED", "SPOILED", "TRANSFER", "EXPLAINED");

    private final PaymentEntryRepository paymentEntryRepository;
    private final InvoiceRepository invoiceRepository;
    private final VoucherAcknowledgementRepository voucherAcknowledgementRepository;

    @Cacheable(VOUCHER_AUDIT)
    public VoucherAuditDTO.Response getCurrentFinancialYearAudit() {
        LocalDate fyStart = currentFinancialYearStart();
        String fyLabel = financialYearLabel(fyStart);

        List<Row> receiptRows = loadRows(paymentEntryRepository.findVoucherRowsFrom(fyStart));
        List<Row> salesRows = loadRows(invoiceRepository.findVoucherRowsFrom(fyStart));

        List<VoucherAcknowledgement> receiptAcks = voucherAcknowledgementRepository
                .findBySeriesAndFinancialYearOrderByRangeStart(VoucherAuditDTO.Series.RECEIPT.name(), fyLabel);
        List<VoucherAcknowledgement> salesAcks = voucherAcknowledgementRepository
                .findBySeriesAndFinancialYearOrderByRangeStart(VoucherAuditDTO.Series.SALES.name(), fyLabel);

        return VoucherAuditDTO.Response.builder()
                .receipts(buildReport(VoucherAuditDTO.Series.RECEIPT, fyLabel, receiptRows, receiptAcks))
                .sales(buildReport(VoucherAuditDTO.Series.SALES, fyLabel, salesRows, salesAcks))
                .build();
    }

    /**
     * Every occurrence of one specific voucher number this financial year, for the "click a used
     * page in the grid" lookup - deliberately outside the cached report and outside buildReport's
     * classification, since this just needs raw matching rows, not gap/duplicate/suspect analysis.
     * Not cached: it's a targeted, cheap query, and caching it per (series, number) would only add
     * bookkeeping for a page that's opened occasionally, not hit repeatedly like the main report.
     */
    @Transactional(readOnly = true)
    public List<VoucherAuditDTO.Occurrence> lookupOccurrences(VoucherAuditDTO.Series series, long vchNumber) {
        LocalDate fyStart = currentFinancialYearStart();
        List<Row> rows = series == VoucherAuditDTO.Series.RECEIPT
                ? loadRows(paymentEntryRepository.findVoucherRowsFrom(fyStart))
                : loadRows(invoiceRepository.findVoucherRowsFrom(fyStart));

        return rows.stream()
                .filter(r -> VoucherNumberParser.extractNumber(r.rawVch())
                        .map(v -> v == vchNumber).orElse(false))
                .sorted(java.util.Comparator.comparing(Row::date))
                .map(r -> VoucherAuditDTO.Occurrence.builder()
                        .id(r.id()).date(r.date()).amount(r.amount())
                        .partyName(r.partyName()).rawValue(r.rawVch())
                        .build())
                .collect(Collectors.toList());
    }

    private List<Row> loadRows(List<Object[]> raw) {
        return raw.stream()
                .map(a -> new Row((Long) a[0], (String) a[1], (LocalDate) a[2], (BigDecimal) a[3], (String) a[4]))
                .toList();
    }

    /**
     * Cheap duplicate/suspect check for exactly one just-saved voucher number - what
     * AdminNotificationListener calls on every payment/invoice save. Deliberately does NOT go
     * through {@link #getCurrentFinancialYearAudit()} or buildReport(): those build book-grid
     * DTOs for every page of every book, exception rows for every ambiguous/unparseable value,
     * and join in every acknowledgement - all irrelevant to "is this one number a problem",
     * and expensive to redo on every single save on a 0.1-vCPU Render instance. This still
     * re-fetches and re-parses the year's rows fresh each time (the cache was already evicted
     * by the save that triggered this check, so there's no valid cached data to reuse), but
     * skips straight to the shared classify() step and stops there.
     */
    @Transactional(readOnly = true)
    public Optional<Anomaly> checkNewVoucher(VoucherAuditDTO.Series series, long vchNumber) {
        LocalDate fyStart = currentFinancialYearStart();
        List<Row> rows = series == VoucherAuditDTO.Series.RECEIPT
                ? loadRows(paymentEntryRepository.findVoucherRowsFrom(fyStart))
                : loadRows(invoiceRepository.findVoucherRowsFrom(fyStart));

        Classification c = classify(rows);
        List<Row> occurrences = c.byValue().get(vchNumber);
        if (occurrences != null && occurrences.size() > 1) {
            return Optional.of(new Anomaly("DUPLICATE", occurrences.size() - 1));
        }
        if (c.suspectValues().contains(vchNumber)) {
            return Optional.of(new Anomaly("SUSPECT", 0));
        }
        return Optional.empty();
    }

    /** type is "DUPLICATE" or "SUSPECT"; otherOccurrences only meaningful for DUPLICATE. */
    public record Anomaly(String type, int otherOccurrences) {}

    // ── Acknowledging a gap ──────────────────────────────────────────────────

    // allEntries=true is load-bearing here, not decoration: this method's own parameters (request,
    // performedBy) have nothing to do with the cache key getCurrentFinancialYearAudit() is stored
    // under (that method takes no arguments, so Spring caches it under a fixed empty key). Without
    // allEntries=true, Spring's default key generator would compute an eviction key from *this*
    // method's arguments instead, which never matches anything - the evict silently no-ops and the
    // stale report keeps being served. Every other @CacheEvict in this app already does this; this
    // one just missed it once and served stale data until traced with a temporary log line.
    @CacheEvict(cacheNames = VOUCHER_AUDIT, allEntries = true)
    @Transactional
    public void acknowledgeGap(VoucherAuditDTO.AcknowledgeRequest request, String performedBy) {
        if (request.getSeries() == null || request.getFinancialYear() == null
                || request.getRangeStart() == null || request.getRangeEnd() == null) {
            throw new IllegalArgumentException("Series, financial year, and range are required.");
        }
        if (request.getRangeStart() > request.getRangeEnd()) {
            throw new IllegalArgumentException("Range start must not be after range end.");
        }
        if (request.getRangeEnd() - request.getRangeStart() + 1 > MAX_ACKNOWLEDGE_RANGE) {
            throw new IllegalArgumentException("That range is too wide to acknowledge in one go - check the numbers.");
        }
        String reason = request.getReasonCategory() == null ? "" : request.getReasonCategory().trim().toUpperCase();
        if (!REASON_CATEGORIES.contains(reason)) {
            throw new IllegalArgumentException("Choose a reason: Voided, Spoiled, Transfer, or Explained.");
        }
        voucherAcknowledgementRepository.save(VoucherAcknowledgement.builder()
                .series(request.getSeries().name())
                .financialYear(request.getFinancialYear())
                .rangeStart(request.getRangeStart())
                .rangeEnd(request.getRangeEnd())
                .reasonCategory(reason)
                .note(request.getNote())
                .acknowledgedBy(performedBy)
                .build());
    }

    /** allEntries=true - see the comment on acknowledgeGap(); the same key mismatch applies here. */
    @CacheEvict(cacheNames = VOUCHER_AUDIT, allEntries = true)
    public void reopenAcknowledgement(Long id) {
        voucherAcknowledgementRepository.deleteById(id);
    }

    // ── Financial year boundary ─────────────────────────────────────────────

    /** 1-Apr of the current financial year (Apr-Mar), per IST "today". */
    private LocalDate currentFinancialYearStart() {
        LocalDate today = IstClock.today();
        int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        return LocalDate.of(fyStartYear, 4, 1);
    }

    private String financialYearLabel(LocalDate fyStart) {
        int startYear = fyStart.getYear();
        return startYear + "-" + String.valueOf((startYear + 1) % 100);
    }

    // ── Row shape used internally, before classification ────────────────────

    private record Row(Long id, String rawVch, LocalDate date, BigDecimal amount, String partyName) {}

    /** The shared, cheap part of classification: every NUMERIC row grouped by value, which of
     *  those values are suspect jumps, and the resulting high-water mark. Stops well short of
     *  building any DTOs - both the full dashboard report and the per-save anomaly check
     *  (checkNewVoucher) start from this same computation, then do very different amounts of
     *  work with it. */
    private record Classification(Map<Long, List<Row>> byValue, Set<Long> suspectValues, long highWaterMark) {}

    private Classification classify(List<Row> rows) {
        Map<Long, List<Row>> byValue = new TreeMap<>();
        for (Row r : rows) {
            if (VoucherNumberParser.parse(r.rawVch()).status() == VoucherNumberParser.Status.NUMERIC) {
                long value = VoucherNumberParser.extractNumber(r.rawVch()).orElseThrow();
                byValue.computeIfAbsent(value, v -> new ArrayList<>()).add(r);
            }
        }

        // Suspect jumps: walk ascending, freezing the high-water mark on any gap over the threshold.
        List<Long> distinctValues = new ArrayList<>(byValue.keySet());
        Collections.sort(distinctValues);
        long highWaterMark = 0;
        Set<Long> suspectValues = new LinkedHashSet<>();
        for (long v : distinctValues) {
            if (v - highWaterMark > SUSPECT_GAP) {
                suspectValues.add(v);
            } else {
                highWaterMark = Math.max(highWaterMark, v);
            }
        }
        return new Classification(byValue, suspectValues, highWaterMark);
    }

    // ── Core pipeline ────────────────────────────────────────────────────────

    private VoucherAuditDTO.SeriesReport buildReport(VoucherAuditDTO.Series series, String fyLabel, List<Row> rows,
                                                       List<VoucherAcknowledgement> acknowledgements) {
        List<VoucherAuditDTO.ExceptionRow> unparseable = new ArrayList<>();
        List<VoucherAuditDTO.ExceptionRow> ambiguous = new ArrayList<>();
        for (Row r : rows) {
            VoucherNumberParser.Result parsed = VoucherNumberParser.parse(r.rawVch());
            switch (parsed.status()) {
                case AMBIGUOUS -> ambiguous.add(toException("AMBIGUOUS", null, List.of(r)));
                case BLANK, UNPARSEABLE -> unparseable.add(toException("UNPARSEABLE", null, List.of(r)));
                case NUMERIC -> { /* handled by classify() below */ }
            }
        }

        Classification c = classify(rows);
        Map<Long, List<Row>> byValue = c.byValue();
        Set<Long> suspectValues = c.suspectValues();
        long highWaterMark = c.highWaterMark();

        List<VoucherAuditDTO.ExceptionRow> duplicates = new ArrayList<>();
        for (var e : byValue.entrySet()) {
            if (e.getValue().size() > 1) {
                duplicates.add(toException("DUPLICATE", e.getKey(), e.getValue()));
            }
        }
        List<VoucherAuditDTO.ExceptionRow> suspects = suspectValues.stream()
                .map(v -> toException("SUSPECT", v, byValue.get(v)))
                .collect(Collectors.toList());

        Set<Long> legitimateUsed = new TreeSet<>(byValue.keySet());
        legitimateUsed.removeAll(suspectValues);

        Set<Long> acknowledgedNumbers = new TreeSet<>();
        for (VoucherAcknowledgement ack : acknowledgements) {
            for (long n = ack.getRangeStart(); n <= ack.getRangeEnd(); n++) {
                acknowledgedNumbers.add(n);
            }
        }

        List<VoucherAuditDTO.Book> books = new ArrayList<>();
        List<VoucherAuditDTO.Run> activeMissingRuns = new ArrayList<>();
        int totalMissing = 0;
        int totalAcknowledged = 0;
        if (highWaterMark > 0) {
            int lastBook = (int) ((highWaterMark - 1) / BOOK_SIZE);
            for (int b = 0; b <= lastBook; b++) {
                long rangeStart = (long) b * BOOK_SIZE + 1;
                long rangeEnd = rangeStart + BOOK_SIZE - 1;
                long ceilingForBook = Math.min(rangeEnd, highWaterMark);

                int used = 0;
                List<VoucherAuditDTO.Run> missingRuns = new ArrayList<>();
                List<VoucherAuditDTO.Run> ackRuns = new ArrayList<>();
                // At most one of these is open at a time - a number is exactly one of used/missing/acknowledged.
                Long missingRunStart = null;
                Long ackRunStart = null;
                for (long n = rangeStart; n <= ceilingForBook; n++) {
                    boolean isUsed = legitimateUsed.contains(n);
                    boolean isAck = !isUsed && acknowledgedNumbers.contains(n);
                    if (missingRunStart != null && (isUsed || isAck)) {
                        missingRuns.add(new VoucherAuditDTO.Run(missingRunStart, n - 1));
                        missingRunStart = null;
                    }
                    if (ackRunStart != null && (isUsed || !isAck)) {
                        ackRuns.add(new VoucherAuditDTO.Run(ackRunStart, n - 1));
                        ackRunStart = null;
                    }
                    if (isUsed) {
                        used++;
                    } else if (isAck) {
                        if (ackRunStart == null) ackRunStart = n;
                    } else {
                        if (missingRunStart == null) missingRunStart = n;
                    }
                }
                if (missingRunStart != null) missingRuns.add(new VoucherAuditDTO.Run(missingRunStart, ceilingForBook));
                if (ackRunStart != null) ackRuns.add(new VoucherAuditDTO.Run(ackRunStart, ceilingForBook));

                int bookMissing = missingRuns.stream().mapToInt(r -> (int) (r.getEnd() - r.getStart() + 1)).sum();
                int bookAcknowledged = ackRuns.stream().mapToInt(r -> (int) (r.getEnd() - r.getStart() + 1)).sum();
                totalMissing += bookMissing;
                totalAcknowledged += bookAcknowledged;
                activeMissingRuns.addAll(missingRuns);

                books.add(VoucherAuditDTO.Book.builder()
                        .bookIndex(b).rangeStart(rangeStart).rangeEnd(rangeEnd)
                        .usedCount(used).missingCount(bookMissing).acknowledgedCount(bookAcknowledged)
                        .missingRuns(missingRuns).acknowledgedRuns(ackRuns)
                        .notYetUsedFrom(ceilingForBook < rangeEnd ? ceilingForBook + 1 : null)
                        .build());
            }
        }

        List<VoucherAuditDTO.ExceptionRow> exceptions = new ArrayList<>();
        exceptions.addAll(duplicates);
        exceptions.addAll(suspects);
        exceptions.addAll(ambiguous);
        exceptions.addAll(unparseable);

        List<VoucherAuditDTO.Acknowledgement> ackDtos = acknowledgements.stream()
                .map(a -> VoucherAuditDTO.Acknowledgement.builder()
                        .id(a.getId()).rangeStart(a.getRangeStart()).rangeEnd(a.getRangeEnd())
                        .reasonCategory(a.getReasonCategory()).note(a.getNote())
                        .acknowledgedBy(a.getAcknowledgedBy()).acknowledgedAt(a.getAcknowledgedAt())
                        .build())
                .collect(Collectors.toList());

        return VoucherAuditDTO.SeriesReport.builder()
                .series(series).financialYear(fyLabel)
                .highWaterMark(highWaterMark)
                .totalUsed(legitimateUsed.size())
                .totalMissing(totalMissing)
                .totalAcknowledged(totalAcknowledged)
                .totalDuplicateValues(duplicates.size())
                .totalSuspect(suspects.size())
                .totalAmbiguous(ambiguous.size())
                .totalUnparseable(unparseable.size())
                .books(books)
                .activeMissingRuns(activeMissingRuns)
                .exceptions(exceptions)
                .acknowledgements(ackDtos)
                .build();
    }

    private VoucherAuditDTO.ExceptionRow toException(String type, Long vchNumber, List<Row> occRows) {
        return VoucherAuditDTO.ExceptionRow.builder()
                .type(type)
                .vchNumber(vchNumber)
                .occurrences(occRows.stream()
                        .map(r -> VoucherAuditDTO.Occurrence.builder()
                                .id(r.id()).date(r.date()).amount(r.amount())
                                .partyName(r.partyName()).rawValue(r.rawVch())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
