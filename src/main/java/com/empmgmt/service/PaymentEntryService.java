package com.empmgmt.service;

import com.empmgmt.dto.EmployeeCollectionSummaryDTO;
import com.empmgmt.dto.PaymentEntryDTO;
import com.empmgmt.dto.TransactionLogDTO;
import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.entity.TransactionLog;
import com.empmgmt.entity.User;
import com.empmgmt.repository.PaymentEntryRepository;
import com.empmgmt.repository.TransactionLogRepository;
import com.empmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Locale;

import static com.empmgmt.config.CacheConfig.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentEntryService {

    private final PaymentEntryRepository paymentEntryRepository;
    private final UserRepository userRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final com.empmgmt.service.ExcelPartyService excelPartyService;

    private static final DateTimeFormatter ENTRY_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter LOG_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm:ss a");
    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");

    // ─────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────

    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
    public PaymentEntryDTO.Response createEntry(PaymentEntryDTO.Request request, String username) {
        // Employees can only create entries for today
        if (request.getEntryDate() != null && !request.getEntryDate().equals(LocalDate.now())) {
            throw new RuntimeException("Entries can only be added for today's date.");
        }
        // Always stamp today's date regardless
        request.setEntryDate(LocalDate.now());
        User employee = findUserByUsername(username);

        // ensure party exists in parties table (accepts "Name" or "Name_GSTIN")
        excelPartyService.ensureExists(request.getPartyName());

        PaymentEntry entry = PaymentEntry.builder()
                .partyName(request.getPartyName())
                .amount(request.getAmount())
                .modeOfPayment(request.getModeOfPayment())
                .entryDate(request.getEntryDate())
                .remarks(request.getRemarks())
                .receiptVchNo(request.getReceiptVchNo() != null && !request.getReceiptVchNo().isBlank()
                        ? request.getReceiptVchNo().trim() : null)
                .employee(employee)
                .build();

        entry = paymentEntryRepository.save(entry);
        log.info("✅ Payment entry SAVED to DB | id={} | party={} | amount={} | employee={} | date={}",
                entry.getId(), entry.getPartyName(), entry.getAmount(), username, entry.getEntryDate());
        logAction("CREATE", entry, username, "Entry created");
        return mapToResponse(entry);
    }

    /**
     * Admin/Accountant can add a payment entry for any date directly from the
     * Party Ledger — used to record a collection that's missing from the
     * statement (a discrepancy found while reviewing it). Unlike the
     * employee flow this is not restricted to today's date; remarks are
     * mandatory so the reason for the out-of-band entry is on record.
     */
    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
    public PaymentEntryDTO.Response createEntryByStaff(PaymentEntryDTO.Request request, String username) {
        if (request.getRemarks() == null || request.getRemarks().isBlank()) {
            throw new RuntimeException("Remarks are mandatory when adding a payment from the ledger");
        }
        User staff = findUserByUsername(username);

        excelPartyService.ensureExists(request.getPartyName());

        PaymentEntry entry = PaymentEntry.builder()
                .partyName(request.getPartyName())
                .amount(request.getAmount())
                .modeOfPayment(request.getModeOfPayment())
                .entryDate(request.getEntryDate())
                .remarks(request.getRemarks())
                .receiptVchNo(request.getReceiptVchNo() != null && !request.getReceiptVchNo().isBlank()
                        ? request.getReceiptVchNo().trim() : null)
                .employee(staff)
                .build();

        entry = paymentEntryRepository.save(entry);
        log.info("✅ Payment entry SAVED to DB via ledger | id={} | party={} | amount={} | staff={} | date={}",
                entry.getId(), entry.getPartyName(), entry.getAmount(), username, entry.getEntryDate());
        logAction("CREATE", entry, username, "Entry added from Party Ledger");
        return mapToResponse(entry);
    }

    // ─────────────────────────────────────────────────────────
    // READ – Employee
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentEntryDTO.Response> getEntriesForEmployee(String username) {
        User employee = findUserByUsername(username);
        return paymentEntryRepository.findByEmployeeOrderByCreatedAtDesc(employee)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentEntryDTO.Response> getTodayEntriesForEmployee(String username) {
        User employee = findUserByUsername(username);
        return paymentEntryRepository
                .findByEmployeeAndEntryDateOrderByCreatedAtDesc(employee, LocalDate.now())
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentEntryDTO.DailySummary getDailySummaryForEmployee(String username) {
        User employee = findUserByUsername(username);
        LocalDate today = LocalDate.now();
        BigDecimal total = paymentEntryRepository.sumAmountByEmployeeAndDate(employee, today);
        long count = paymentEntryRepository.countByEmployeeAndDate(employee, today);
        return PaymentEntryDTO.DailySummary.builder()
                .date(today).totalEntries(count)
                .totalAmount(total != null ? total : BigDecimal.ZERO)
                .employeeName(employee.getFullName())
                .build();
    }

    /**
     * Returns all entries for an employee grouped by entryDate descending.
     * Each DayGroup contains the date label, totals and the flat list of entries.
     */
    @Transactional(readOnly = true)
    public List<PaymentEntryDTO.DayGroup> getEntriesGroupedByDayForEmployee(String username) {
        List<PaymentEntryDTO.Response> all = getEntriesForEmployee(username);

        Map<LocalDate, List<PaymentEntryDTO.Response>> grouped = all.stream()
                .collect(Collectors.groupingBy(PaymentEntryDTO.Response::getEntryDate));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<PaymentEntryDTO.Response>>comparingByKey().reversed())
                .map(e -> {
                    BigDecimal dayTotal = e.getValue().stream()
                            .map(PaymentEntryDTO.Response::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return PaymentEntryDTO.DayGroup.builder()
                            .date(e.getKey())
                            .dateFormatted(e.getKey().format(DAY_FORMATTER))
                            .totalEntries(e.getValue().size())
                            .totalAmount(dayTotal)
                            .entries(e.getValue())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    // READ – Admin
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentEntryDTO.Response> getAllEntries() {
        return paymentEntryRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentEntryDTO.Response> getFilteredEntriesForAdmin(LocalDate from, LocalDate to, Long employeeId) {
        LocalDate start = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate end   = to   != null ? to   : LocalDate.now();
        if (employeeId != null) {
            return paymentEntryRepository.findByEmployeeIdAndDateRange(employeeId, start, end)
                    .stream().map(this::mapToResponse).collect(Collectors.toList());
        }
        return paymentEntryRepository.findAllByDateRange(start, end)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentEntryDTO.DayGroup> getFilteredEntriesGroupedByDayForEmployee(String username, LocalDate from, LocalDate to) {
        User employee = findUserByUsername(username);
        LocalDate start = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate end   = to   != null ? to   : LocalDate.now();
        List<PaymentEntryDTO.Response> all = paymentEntryRepository
                .findByEmployeeIdAndDateRange(employee.getId(), start, end)
                .stream().map(this::mapToResponse).collect(Collectors.toList());

        Map<LocalDate, List<PaymentEntryDTO.Response>> grouped = all.stream()
                .collect(Collectors.groupingBy(PaymentEntryDTO.Response::getEntryDate));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<PaymentEntryDTO.Response>>comparingByKey().reversed())
                .map(e -> {
                    BigDecimal dayTotal = e.getValue().stream()
                            .map(PaymentEntryDTO.Response::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return PaymentEntryDTO.DayGroup.builder()
                            .date(e.getKey())
                            .dateFormatted(e.getKey().format(DAY_FORMATTER))
                            .totalEntries(e.getValue().size())
                            .totalAmount(dayTotal)
                            .entries(e.getValue())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentEntryDTO.Response> getEntriesForEmployeeById(Long employeeId) {
        return paymentEntryRepository.findByEmployeeId(employeeId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentEntryDTO.Response getEntryById(Long id) {
        return mapToResponse(findEntryById(id));
    }

    // ─────────────────────────────────────────────────────────
    // DASHBOARD ANALYTICS
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats() {
        LocalDate today = LocalDate.now();
        LocalDate since30 = today.minusDays(29);
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("dd MMM").withLocale(Locale.ENGLISH);

        List<PaymentEntry> allEntries = paymentEntryRepository.findAll();

        // Daily totals for last 30 days (fill zeros for missing days)
        Map<LocalDate, BigDecimal> dailyMap = new java.util.LinkedHashMap<>();
        allEntries.stream()
                .filter(e -> !e.getEntryDate().isBefore(since30) && !e.getEntryDate().isAfter(today))
                .forEach(e -> dailyMap.merge(e.getEntryDate(), e.getAmount(), BigDecimal::add));
        List<String> dailyLabels = new ArrayList<>();
        List<Double> dailyAmounts = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            dailyLabels.add(d.format(labelFmt));
            dailyAmounts.add(dailyMap.getOrDefault(d, BigDecimal.ZERO).doubleValue());
        }

        // Mode breakdown (all-time), sorted desc
        Map<String, BigDecimal> modeMap = new java.util.LinkedHashMap<>();
        allEntries.forEach(e -> modeMap.merge(e.getModeOfPayment().getDisplayName(), e.getAmount(), BigDecimal::add));
        List<Map.Entry<String, BigDecimal>> sortedModes = modeMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toList());
        List<String> modeLabels = sortedModes.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        List<Double> modeAmounts = sortedModes.stream().map(e -> e.getValue().doubleValue()).collect(Collectors.toList());

        // Top 5 parties (all-time), sorted desc
        Map<String, BigDecimal> partyMap = new java.util.LinkedHashMap<>();
        allEntries.forEach(e -> partyMap.merge(e.getPartyName(), e.getAmount(), BigDecimal::add));
        List<Map<String, Object>> top5Parties = partyMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("amount", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        // Today's total and count
        BigDecimal todayTotal = allEntries.stream()
                .filter(e -> e.getEntryDate().equals(today))
                .map(PaymentEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long todayCount = allEntries.stream().filter(e -> e.getEntryDate().equals(today)).count();

        // Recent entries (top 10, by createdAt desc) — replaces second DB call in controller
        List<PaymentEntryDTO.Response> recentEntries = allEntries.stream()
                .sorted(Comparator.comparing(PaymentEntry::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("dailyLabels", dailyLabels);
        stats.put("dailyAmounts", dailyAmounts);
        stats.put("modeLabels", modeLabels);
        stats.put("modeAmounts", modeAmounts);
        stats.put("top5Parties", top5Parties);
        stats.put("todayTotal", todayTotal);
        stats.put("todayCount", todayCount);
        stats.put("recentEntries", recentEntries);
        stats.put("totalEntries", (long) allEntries.size());
        return stats;
    }

    /**
     * How much each employee has brought in — today, month-to-date, and all-time.
     * Read-only summary; does not expose individual entries (that stays admin-only
     * via {@link #getAllEntries()} / {@link #getEntriesForEmployeeById(Long)}).
     */
    @Transactional(readOnly = true)
    public List<EmployeeCollectionSummaryDTO> getEmployeeCollectionSummaries() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        Map<Long, Object[]> allTimeByEmployee = paymentEntryRepository.sumAndCountGroupedByEmployee().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));
        Map<Long, Object[]> monthByEmployee = paymentEntryRepository
                .sumAndCountGroupedByEmployeeInRange(monthStart, today).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        return userRepository.findByRole(User.Role.EMPLOYEE).stream()
                .map(emp -> {
                    Object[] allTime = allTimeByEmployee.get(emp.getId());
                    Object[] month = monthByEmployee.get(emp.getId());
                    BigDecimal todayAmount = paymentEntryRepository.sumAmountByEmployeeAndDate(emp, today);
                    long todayCount = paymentEntryRepository.countByEmployeeAndDate(emp, today);

                    return EmployeeCollectionSummaryDTO.builder()
                            .employeeId(emp.getId())
                            .fullName(emp.getFullName())
                            .username(emp.getUsername())
                            .todayAmount(todayAmount != null ? todayAmount : BigDecimal.ZERO)
                            .todayCount(todayCount)
                            .monthAmount(month != null ? (BigDecimal) month[1] : BigDecimal.ZERO)
                            .monthCount(month != null ? (Long) month[2] : 0L)
                            .allTimeAmount(allTime != null ? (BigDecimal) allTime[1] : BigDecimal.ZERO)
                            .allTimeCount(allTime != null ? (Long) allTime[2] : 0L)
                            .build();
                })
                .sorted(Comparator.comparing(EmployeeCollectionSummaryDTO::getAllTimeAmount).reversed())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────

    /** Admin can update any entry (any date). Remarks mandatory. */
    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
    public PaymentEntryDTO.Response updateEntry(Long id, PaymentEntryDTO.Request request, String performedBy) {
        if (request.getRemarks() == null || request.getRemarks().isBlank()) {
            throw new RuntimeException("Remarks are mandatory when editing an entry");
        }
        PaymentEntry entry = findEntryById(id);
        String diff = computeDiff(entry, request);

        // ensure party exists for updated value
        excelPartyService.ensureExists(request.getPartyName());
        entry.setPartyName(request.getPartyName());
        entry.setAmount(request.getAmount());
        entry.setModeOfPayment(request.getModeOfPayment());
        entry.setEntryDate(request.getEntryDate());
        entry.setRemarks(request.getRemarks());
        entry.setReceiptVchNo(request.getReceiptVchNo() != null && !request.getReceiptVchNo().isBlank()
                ? request.getReceiptVchNo().trim() : null);
        entry.setEdited(true);
        entry.setEditedBy("ADMIN");
        entry.setEditedAt(java.time.LocalDateTime.now());

        entry = paymentEntryRepository.save(entry);
        logAction("UPDATE", entry, performedBy, diff);
        return mapToResponse(entry);
    }

    /**
     * Employee can only update their own entry AND only if entryDate == today.
     * Remarks are mandatory.
     */
    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
    public PaymentEntryDTO.Response updateEntryByEmployee(Long id, PaymentEntryDTO.Request request, String username) {
        if (request.getRemarks() == null || request.getRemarks().isBlank()) {
            throw new RuntimeException("Remarks are mandatory when editing an entry");
        }
        PaymentEntry entry = findEntryById(id);
        if (!entry.getEmployee().getUsername().equals(username)) {
            throw new AccessDeniedException("You can only edit your own entries");
        }
        if (!entry.getEntryDate().equals(LocalDate.now())) {
            throw new RuntimeException("You can only edit today's entries. Past entries cannot be modified.");
        }

        String diff = computeDiff(entry, request);

        // ensure party exists for updated value
        excelPartyService.ensureExists(request.getPartyName());
        entry.setPartyName(request.getPartyName());
        entry.setAmount(request.getAmount());
        entry.setModeOfPayment(request.getModeOfPayment());
        entry.setEntryDate(request.getEntryDate());
        entry.setRemarks(request.getRemarks());
        entry.setReceiptVchNo(request.getReceiptVchNo() != null && !request.getReceiptVchNo().isBlank()
                ? request.getReceiptVchNo().trim() : null);
        entry.setEdited(true);
        entry.setEditedBy("EMPLOYEE");
        entry.setEditedAt(java.time.LocalDateTime.now());

        entry = paymentEntryRepository.save(entry);
        logAction("UPDATE", entry, username, diff);
        return mapToResponse(entry);
    }

    // ─────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────

    /** Admin can delete any entry */
    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
    public void deleteEntry(Long id, String performedBy) {
        PaymentEntry entry = findEntryById(id);
        logAction("DELETE", entry, performedBy, "Entry deleted");
        paymentEntryRepository.deleteById(id);
    }

    /** Employee can only delete their own entry */
    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS}, allEntries = true)
    public void deleteEntryByEmployee(Long id, String username) {
        PaymentEntry entry = findEntryById(id);
        if (!entry.getEmployee().getUsername().equals(username)) {
            throw new AccessDeniedException("You can only delete your own entries");
        }
        deleteEntry(id, username);
    }

    // ─────────────────────────────────────────────────────────
    // TRANSACTION LOG
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TransactionLogDTO.Response> getTransactionLogsForEmployee(String username) {
        return transactionLogRepository
                .findByEmployeeUsernameOrderByPerformedAtDesc(username)
                .stream().map(this::mapLogToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionLogDTO.Response> getAllTransactionLogs() {
        return transactionLogRepository.findAllByOrderByPerformedAtDesc()
                .stream().map(this::mapLogToResponse).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────

    private void logAction(String action, PaymentEntry entry, String performedBy, String notes) {
        TransactionLog log = TransactionLog.builder()
                .action(action)
                .entryId(entry.getId())
                .employeeName(entry.getEmployee().getFullName())
                .employeeUsername(entry.getEmployee().getUsername())
                .partyName(entry.getPartyName())
                .amount(entry.getAmount())
                .modeOfPayment(entry.getModeOfPayment().getDisplayName())
                .entryDate(entry.getEntryDate())
                .remarks(entry.getRemarks())
                .performedBy(performedBy)
                .notes(notes)
                .build();
        transactionLogRepository.save(log);
    }

    private String computeDiff(PaymentEntry before, PaymentEntryDTO.Request after) {
        List<String> changes = new ArrayList<>();
        if (!before.getPartyName().equals(after.getPartyName()))
            changes.add("Party: \"" + before.getPartyName() + "\" → \"" + after.getPartyName() + "\"");
        if (before.getAmount().compareTo(after.getAmount()) != 0)
            changes.add("Amount: ₹" + before.getAmount() + " → ₹" + after.getAmount());
        if (!before.getModeOfPayment().equals(after.getModeOfPayment()))
            changes.add("Mode: " + before.getModeOfPayment().getDisplayName()
                    + " → " + after.getModeOfPayment().getDisplayName());
        if (!before.getEntryDate().equals(after.getEntryDate()))
            changes.add("Date: " + before.getEntryDate() + " → " + after.getEntryDate());
        if (!Objects.equals(before.getRemarks(), after.getRemarks()))
            changes.add("Remarks: \"" + before.getRemarks() + "\" → \"" + after.getRemarks() + "\"");
        return changes.isEmpty() ? "No changes detected" : String.join("; ", changes);
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    private PaymentEntry findEntryById(Long id) {
        return paymentEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found: " + id));
    }

    private PaymentEntryDTO.Response mapToResponse(PaymentEntry entry) {
        return PaymentEntryDTO.Response.builder()
                .id(entry.getId())
                .partyName(entry.getPartyName())
                .amount(entry.getAmount())
                .modeOfPayment(entry.getModeOfPayment().getDisplayName())
                .entryDate(entry.getEntryDate())
                .remarks(entry.getRemarks())
                .receiptVchNo(entry.getReceiptVchNo())
                .employeeName(entry.getEmployee().getFullName())
                .employeeUsername(entry.getEmployee().getUsername())
                .createdAt(entry.getCreatedAt() != null
                        ? entry.getCreatedAt().format(ENTRY_FORMATTER) : "")
                .updatedAt(entry.getUpdatedAt() != null
                        ? entry.getUpdatedAt().format(ENTRY_FORMATTER) : "")
                .edited(entry.isEdited())
                .editedBy(entry.getEditedBy())
                .editedAt(entry.getEditedAt() != null
                        ? entry.getEditedAt().format(ENTRY_FORMATTER) : null)
                .build();
    }

    private TransactionLogDTO.Response mapLogToResponse(TransactionLog log) {
        return TransactionLogDTO.Response.builder()
                .id(log.getId())
                .action(log.getAction())
                .entryId(log.getEntryId())
                .employeeName(log.getEmployeeName())
                .employeeUsername(log.getEmployeeUsername())
                .partyName(log.getPartyName())
                .amount(log.getAmount())
                .modeOfPayment(log.getModeOfPayment())
                .entryDate(log.getEntryDate())
                .remarks(log.getRemarks())
                .performedBy(log.getPerformedBy())
                .notes(log.getNotes())
                .performedAt(log.getPerformedAt() != null
                        ? log.getPerformedAt().format(LOG_FORMATTER) : "")
                .build();
    }
}
