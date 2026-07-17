package com.empmgmt.controller;

import com.empmgmt.dto.InvoiceDTO;
import com.empmgmt.dto.PartyDTO;
import com.empmgmt.dto.PaymentEntryDTO;
import com.empmgmt.dto.UserDTO;
import com.empmgmt.entity.Party;
import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.repository.NotificationLogRepository;
import com.empmgmt.repository.PartyRepository;
import com.empmgmt.service.ExportService;
import com.empmgmt.service.InvoiceService;
import com.empmgmt.service.OutstandingNotificationService;
import com.empmgmt.service.PaymentEntryService;
import com.empmgmt.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final PaymentEntryService paymentEntryService;
    private final UserService userService;
    private final ExportService exportService;
    private final InvoiceService invoiceService;
    private final OutstandingNotificationService notificationService;
    private final NotificationLogRepository notificationLogRepository;
    private final PartyRepository partyRepository;

    // ─── Dashboard ────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        var employees = userService.getAllEmployees();
        model.addAttribute("employees", employees);
        model.addAttribute("activeEmployeeCount",
                employees.stream().filter(e -> e.isActive()).count());
        model.addAttribute("adminName", auth.getName());
        Map<String, Object> stats = paymentEntryService.getDashboardStats();
        stats.forEach(model::addAttribute);
        return "admin/dashboard";
    }

    // ─── Employee Management ──────────────────────────────────

    @GetMapping("/employees")
    public String listEmployees(Model model) {
        model.addAttribute("employees", userService.getAllEmployees());
        model.addAttribute("accountants", userService.getAllAccountants());
        model.addAttribute("managers", userService.getAllManagers());
        model.addAttribute("newEmployee", new UserDTO.CreateRequest());
        return "admin/employees";
    }

    @PostMapping("/employees/add")
    public String addEmployee(@Valid @ModelAttribute("newEmployee") UserDTO.CreateRequest request,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("employees", userService.getAllEmployees());
            model.addAttribute("accountants", userService.getAllAccountants());
            model.addAttribute("managers", userService.getAllManagers());
            return "admin/employees";
        }
        try {
            userService.createEmployee(request);
            String roleLabel;
            switch (request.getRole() != null ? request.getRole() : com.empmgmt.entity.User.Role.EMPLOYEE) {
                case ACCOUNTANT: roleLabel = "Accountant"; break;
                case MANAGER: roleLabel = "Manager"; break;
                default: roleLabel = "Employee";
            }
            redirectAttributes.addFlashAttribute("successMsg", roleLabel + " created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/employees";
    }

    @GetMapping("/employees/{id}")
    public String viewEmployee(@PathVariable Long id, Model model) {
        model.addAttribute("employee", userService.getUserById(id));
        model.addAttribute("entries", paymentEntryService.getEntriesForEmployeeById(id));
        model.addAttribute("editEntry", new PaymentEntryDTO.Request());
        model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
        return "admin/employee-detail";
    }

    @PostMapping("/employees/{id}/toggle")
    public String toggleEmployeeStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.toggleStatus(id);
        redirectAttributes.addFlashAttribute("successMsg", "Employee status updated.");
        return "redirect:/admin/employees";
    }

    @PostMapping("/employees/{id}/reset-password")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam String newPassword,
                                RedirectAttributes redirectAttributes) {
        userService.resetPassword(id, newPassword);
        redirectAttributes.addFlashAttribute("successMsg", "Password reset successfully.");
        return "redirect:/admin/employees/" + id;
    }

    // ─── Entry Management ─────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/entries")
    public String allEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            Model model, Authentication auth) {
        boolean filtered = from != null || to != null || employeeId != null;
        model.addAttribute("entries", filtered
                ? paymentEntryService.getFilteredEntriesForAdmin(from, to, employeeId)
                : paymentEntryService.getAllEntries());
        model.addAttribute("employees", userService.getAllEmployees());
        model.addAttribute("adminName", auth.getName());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("selectedEmployeeId", employeeId);
        return "admin/entries";
    }

    @GetMapping("/entries/{id}/edit")
    public String editEntryForm(@PathVariable Long id, Model model) {
        PaymentEntryDTO.Response entry = paymentEntryService.getEntryById(id);
        model.addAttribute("entry", entry);
        model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());

        PaymentEntryDTO.Request req = new PaymentEntryDTO.Request();
        req.setPartyName(entry.getPartyName());
        req.setAmount(entry.getAmount());
        req.setRemarks(entry.getRemarks());
        req.setEntryDate(entry.getEntryDate());
        req.setReceiptVchNo(entry.getReceiptVchNo());
        model.addAttribute("editRequest", req);
        return "admin/edit-entry";
    }

    @PostMapping("/entries/{id}/edit")
    public String editEntry(@PathVariable Long id,
                            @Valid @ModelAttribute("editRequest") PaymentEntryDTO.Request request,
                            BindingResult result,
                            Authentication auth,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("entry", paymentEntryService.getEntryById(id));
            model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
            return "admin/edit-entry";
        }
        paymentEntryService.updateEntry(id, request, auth.getName());
        redirectAttributes.addFlashAttribute("successMsg", "Entry updated successfully!");
        return "redirect:/admin/entries";
    }

    @PostMapping("/entries/{id}/delete")
    public String deleteEntry(@PathVariable Long id,
                              Authentication auth,
                              RedirectAttributes redirectAttributes) {
        paymentEntryService.deleteEntry(id, auth.getName());
        redirectAttributes.addFlashAttribute("successMsg", "Entry deleted successfully.");
        return "redirect:/admin/entries";
    }

    // ─── Reports & Export ─────────────────────────────────────

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId) {
        try {
            var entries = paymentEntryService.getFilteredEntriesForAdmin(from, to, employeeId);
            byte[] data = exportService.exportEntriesToExcel(entries);
            String filename = buildExportFilename("payment-entries", from, to) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (IOException e) {
            log.error("Excel export failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId) {
        try {
            var entries = paymentEntryService.getFilteredEntriesForAdmin(from, to, employeeId);
            byte[] data = exportService.exportEntriesToPdf(entries, from, to);
            String filename = buildExportFilename("payment-entries", from, to) + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(data);
        } catch (com.lowagie.text.DocumentException e) {
            log.error("PDF export failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/audit-csv")
    public ResponseEntity<byte[]> exportAuditCsv() {
        var logs = paymentEntryService.getAllTransactionLogs();
        byte[] data = exportService.exportAuditLogToCsv(logs);
        String filename = "audit-log_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }

    private String buildExportFilename(String prefix, LocalDate from, LocalDate to) {
        String f = from != null ? from.toString() : "all";
        String t = to   != null ? to.toString()   : LocalDate.now().toString();
        return prefix + "_" + f + "_to_" + t;
    }

    // ─── Transaction History (Audit Log) ──────────────────────

    @GetMapping("/history")
    public String history(Model model, Authentication auth) {
        model.addAttribute("logs", paymentEntryService.getAllTransactionLogs());
        model.addAttribute("adminName", auth.getName());
        return "admin/history";
    }

    // ─── Invoices & Outstanding ────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/invoices")
    public String invoices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model, Authentication auth) {
        boolean filtered = from != null || to != null;
        model.addAttribute("invoices", filtered
                ? invoiceService.getInvoicesByDateRange(from, to)
                : invoiceService.getAllInvoices());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("outstandingSummary", invoiceService.getPartyOutstandingSummary());
        InvoiceDTO.Request newInvoice = new InvoiceDTO.Request();
        newInvoice.setInvoiceNumber(invoiceService.getNextInvoiceNumber());
        model.addAttribute("newInvoice", newInvoice);
        model.addAttribute("deliveryModes", com.empmgmt.entity.Invoice.DeliveryMode.values());
        invoiceService.getInvoicePageStats().forEach(model::addAttribute);
        model.addAttribute("recentNotifications", notificationLogRepository.findTop30ByOrderBySentAtDesc());
        model.addAttribute("adminName", auth.getName());
        return "admin/invoices";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/invoices/add")
    public String addInvoice(@Valid @ModelAttribute("newInvoice") InvoiceDTO.Request request,
                             BindingResult result,
                             Model model,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("invoices", invoiceService.getAllInvoices());
            model.addAttribute("outstandingSummary", invoiceService.getPartyOutstandingSummary());
            model.addAttribute("deliveryModes", com.empmgmt.entity.Invoice.DeliveryMode.values());
            invoiceService.getInvoicePageStats().forEach(model::addAttribute);
            model.addAttribute("recentNotifications", notificationLogRepository.findTop30ByOrderBySentAtDesc());
            model.addAttribute("adminName", auth.getName());
            return "admin/invoices";
        }
        try {
            invoiceService.createInvoice(request, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Invoice added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/invoices";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/invoices/{id}/delete")
    public String deleteInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.deleteInvoice(id);
            redirectAttributes.addFlashAttribute("successMsg", "Invoice deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/invoices";
    }

    // ─── Party Ledger (Statement of Account) ──────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/ledger")
    public String partyLedger(@RequestParam String partyName, Model model, Authentication auth) {
        model.addAttribute("ledger", invoiceService.getPartyLedger(partyName));
        model.addAttribute("adminName", auth.getName());
        PaymentEntryDTO.Request newPayment = new PaymentEntryDTO.Request();
        newPayment.setEntryDate(LocalDate.now());
        model.addAttribute("newPayment", newPayment);
        model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
        return "admin/party-ledger";
    }

    /**
     * Lets Admin/Accountant record a collection directly from a party's ledger —
     * for a payment that's missing from the statement (a discrepancy caught while
     * reviewing it), not routed through the normal employee day-entry flow.
     */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/ledger/add-payment")
    public String addLedgerPayment(@Valid @ModelAttribute("newPayment") PaymentEntryDTO.Request request,
                                   BindingResult result,
                                   Authentication auth,
                                   RedirectAttributes redirectAttributes) {
        String encodedParty = URLEncoder.encode(
                request.getPartyName() == null ? "" : request.getPartyName(), StandardCharsets.UTF_8);
        if (result.hasErrors()) {
            String firstError = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .findFirst().orElse("Invalid payment details.");
            redirectAttributes.addFlashAttribute("errorMsg", firstError);
            return "redirect:/admin/ledger?partyName=" + encodedParty;
        }
        try {
            paymentEntryService.createEntryByStaff(request, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Payment entry added to ledger.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/ledger?partyName=" + encodedParty;
    }

    /** Every party's statement of account on one page — mirrors the Excel "Party Ledger" sheet. */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/full-ledger")
    public String fullLedger(Model model, Authentication auth) {
        var ledgers = invoiceService.getAllPartyLedgers();
        model.addAttribute("ledgers", ledgers);
        model.addAttribute("adminName", auth.getName());
        return "admin/full-ledger";
    }

    /**
     * One party's transaction table, fetched lazily (collapsed by default on the Full Ledger
     * page) so the initial page load doesn't render all 174 parties' full history at once -
     * expensive on a CPU-constrained free-tier instance. Reads from the already-cached
     * {@link InvoiceService#getAllPartyLedgers()} list (called via the injected bean, so it
     * goes through the Spring proxy and actually hits the cache) - zero extra DB round trips.
     */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/full-ledger/party")
    public String fullLedgerPartyDetail(@RequestParam String partyName, Model model) {
        var ledger = invoiceService.getAllPartyLedgers().stream()
                .filter(p -> p.getPartyName().equals(partyName))
                .findFirst()
                .orElse(null);
        model.addAttribute("ledger", ledger);
        return "admin/full-ledger :: partyTable";
    }

    // ─── Aging Report ──────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/aging")
    public String agingReport(Model model, Authentication auth) {
        var agingList = invoiceService.getAgingReport();

        Map<String, java.math.BigDecimal> bucketTotals = new java.util.LinkedHashMap<>();
        for (String bucket : List.of("0-30", "31-60", "61-90", "90+")) {
            bucketTotals.put(bucket, java.math.BigDecimal.ZERO);
        }
        for (var row : agingList) {
            bucketTotals.merge(row.getBucket(), row.getOutstanding(), java.math.BigDecimal::add);
        }

        model.addAttribute("agingList", agingList);
        model.addAttribute("bucketTotals", bucketTotals);
        model.addAttribute("adminName", auth.getName());
        return "admin/aging";
    }

    // ─── Party Management ─────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/parties")
    public String listParties(Model model, Authentication auth,
                              @RequestParam(required = false) String q) {
        var parties = (q != null && !q.isBlank())
                ? partyRepository.findTop50ByCombinedContainingIgnoreCase(q)
                : partyRepository.findAll(Sort.by("name"));
        model.addAttribute("parties", parties);
        model.addAttribute("newParty", new PartyDTO.Request());
        model.addAttribute("q", q);
        model.addAttribute("adminName", auth.getName());
        return "admin/parties";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/parties/add")
    public String addParty(@Valid @ModelAttribute("newParty") PartyDTO.Request request,
                           BindingResult result,
                           Authentication auth,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("parties", partyRepository.findAll(
                    Sort.by("name")));
            model.addAttribute("adminName", auth.getName());
            return "admin/parties";
        }
        try {
            String gstin    = request.getGstin() != null ? request.getGstin().trim() : "";
            String combined = gstin.isEmpty() ? request.getName().trim()
                                              : request.getName().trim() + "_" + gstin;
            if (partyRepository.existsByCombined(combined)) {
                redirectAttributes.addFlashAttribute("errorMsg",
                        "Party '" + combined + "' already exists.");
                return "redirect:/admin/parties";
            }
            partyRepository.save(Party.builder()
                    .name(request.getName().trim())
                    .gst(gstin)
                    .combined(combined)
                    .trailingNumber(request.getTrailingNumber() != null && !request.getTrailingNumber().isBlank()
                            ? request.getTrailingNumber().trim() : null)
                    .phone(request.getPhone() != null && !request.getPhone().isBlank()
                            ? request.getPhone().trim() : null)
                    .whatsappOptIn(request.isWhatsappOptIn())
                    .build());
            redirectAttributes.addFlashAttribute("successMsg", "Party added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/parties";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/parties/{id}/edit")
    public String editPartyForm(@PathVariable Long id, Model model, Authentication auth) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Party not found"));
        PartyDTO.Request req = PartyDTO.Request.builder()
                .name(party.getName())
                .gstin(party.getGst())
                .trailingNumber(party.getTrailingNumber())
                .phone(party.getPhone())
                .whatsappOptIn(party.isWhatsappOptIn())
                .build();
        model.addAttribute("party", party);
        model.addAttribute("editRequest", req);
        model.addAttribute("adminName", auth.getName());
        return "admin/edit-party";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/parties/{id}/edit")
    public String editParty(@PathVariable Long id,
                            @Valid @ModelAttribute("editRequest") PartyDTO.Request request,
                            BindingResult result,
                            Authentication auth,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("party", partyRepository.findById(id).orElse(null));
            model.addAttribute("adminName", auth.getName());
            return "admin/edit-party";
        }
        partyRepository.findById(id).ifPresent(party -> {
            String gstin    = request.getGstin() != null ? request.getGstin().trim() : "";
            String combined = gstin.isEmpty() ? request.getName().trim()
                                              : request.getName().trim() + "_" + gstin;
            // Check uniqueness only if combined changed
            if (!combined.equals(party.getCombined()) && partyRepository.existsByCombined(combined)) {
                throw new RuntimeException("Party '" + combined + "' already exists.");
            }
            party.setName(request.getName().trim());
            party.setGst(gstin);
            party.setCombined(combined);
            party.setTrailingNumber(request.getTrailingNumber() != null && !request.getTrailingNumber().isBlank()
                    ? request.getTrailingNumber().trim() : null);
            party.setPhone(request.getPhone() != null && !request.getPhone().isBlank()
                    ? request.getPhone().trim() : null);
            party.setWhatsappOptIn(request.isWhatsappOptIn());
            partyRepository.save(party);
        });
        redirectAttributes.addFlashAttribute("successMsg", "Party updated successfully!");
        return "redirect:/admin/parties";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/parties/{id}/delete")
    public String deleteParty(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        partyRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMsg", "Party deleted.");
        return "redirect:/admin/parties";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/parties/{id}/phone")
    public String updatePartyPhone(@PathVariable Long id,
                                   @RequestParam(required = false) String phone,
                                   @RequestParam(defaultValue = "false") boolean whatsappOptIn,
                                   RedirectAttributes redirectAttributes) {
        partyRepository.findById(id).ifPresent(party -> {
            party.setPhone(phone != null && !phone.isBlank() ? phone.trim() : null);
            party.setWhatsappOptIn(whatsappOptIn);
            partyRepository.save(party);
        });
        redirectAttributes.addFlashAttribute("successMsg", "Party contact updated.");
        return "redirect:/admin/invoices";
    }

    // ─── Manual Notification Trigger ─────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/notifications/send")
    public String sendNotificationsNow(Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            var logs = notificationService.sendDailyReminders(triggeredByLabel(auth));
            long sent    = logs.stream().filter(l -> l.getStatus() == com.empmgmt.entity.NotificationLog.Status.SENT).count();
            long dryRun  = logs.stream().filter(l -> l.getStatus() == com.empmgmt.entity.NotificationLog.Status.DRY_RUN).count();
            long failed  = logs.stream().filter(l -> l.getStatus() == com.empmgmt.entity.NotificationLog.Status.FAILED).count();
            redirectAttributes.addFlashAttribute("successMsg",
                    "Reminders sent — SENT: " + sent + "  DRY-RUN: " + dryRun + "  FAILED: " + failed);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Send failed: " + e.getMessage());
        }
        return "redirect:/admin/invoices";
    }

    // ─── Notification Analytics ───────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/notifications")
    public String notificationAnalytics(Model model, Authentication auth) {
        model.addAttribute("stats", notificationService.getStats());
        model.addAttribute("recentNotifications", notificationLogRepository.findTop30ByOrderBySentAtDesc());
        model.addAttribute("dailyReminderEnabled", notificationService.isDailyReminderEnabled());
        model.addAttribute("adminName", auth.getName());
        return "admin/notifications";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/notifications/toggle-schedule")
    public String toggleSchedule(Authentication auth, RedirectAttributes redirectAttributes) {
        boolean nowEnabled = notificationService.toggleDailyReminder(auth.getName());
        redirectAttributes.addFlashAttribute("successMsg",
                "Daily 5 PM reminders are now " + (nowEnabled ? "ON ✅" : "OFF ⛔"));
        return "redirect:/admin/notifications";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/notifications/resend-failed")
    public String resendFailed(Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            var logs = notificationService.resendFailed(triggeredByLabel(auth));
            if (logs.isEmpty()) {
                redirectAttributes.addFlashAttribute("successMsg", "No recently-failed parties to resend to.");
            } else {
                long sent   = logs.stream().filter(l -> l.getStatus() == com.empmgmt.entity.NotificationLog.Status.SENT).count();
                long failed = logs.stream().filter(l -> l.getStatus() == com.empmgmt.entity.NotificationLog.Status.FAILED).count();
                redirectAttributes.addFlashAttribute("successMsg",
                        "Resend complete — SENT: " + sent + "  FAILED: " + failed + " (of " + logs.size() + " retried)");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Resend failed: " + e.getMessage());
        }
        return "redirect:/admin/notifications";
    }

    // ─── Employee Collections (read-only, for Accountant + Admin) ─────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/employee-collections")
    public String employeeCollections(Model model, Authentication auth) {
        var summaries = paymentEntryService.getEmployeeCollectionSummaries();

        java.math.BigDecimal todayTotal = summaries.stream()
                .map(com.empmgmt.dto.EmployeeCollectionSummaryDTO::getTodayAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal monthTotal = summaries.stream()
                .map(com.empmgmt.dto.EmployeeCollectionSummaryDTO::getMonthAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal allTimeTotal = summaries.stream()
                .map(com.empmgmt.dto.EmployeeCollectionSummaryDTO::getAllTimeAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        model.addAttribute("summaries", summaries);
        model.addAttribute("todayTotal", todayTotal);
        model.addAttribute("monthTotal", monthTotal);
        model.addAttribute("allTimeTotal", allTimeTotal);
        model.addAttribute("adminName", auth.getName());
        return "admin/employee-collections";
    }

    // ─── Executive Dashboard ───────────────────────────────────

    @GetMapping("/executive")
    public String executiveDashboard(Model model, Authentication auth) {
        model.addAttribute("summary", invoiceService.getExecutiveSummary());
        model.addAttribute("adminName", auth.getName());
        return "admin/executive-dashboard";
    }

    // ─── Collections Dashboard ─────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/collections")
    public String collectionsDashboard(Model model, Authentication auth) {
        var worklist = notificationService.getCollectionsWorklist();

        java.math.BigDecimal totalToCollect = worklist.stream()
                .map(com.empmgmt.dto.CollectionItemDTO::getOutstanding)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        long highPriorityCount = worklist.stream()
                .filter(w -> w.getBucket().equals("61-90") || w.getBucket().equals("90+"))
                .count();
        java.math.BigDecimal highPriorityAmount = worklist.stream()
                .filter(w -> w.getBucket().equals("61-90") || w.getBucket().equals("90+"))
                .map(com.empmgmt.dto.CollectionItemDTO::getOutstanding)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        long neverContactedCount = worklist.stream().filter(w -> w.getLastReminderSentAt() == null).count();
        long contactedRecentCount = worklist.stream()
                .filter(w -> w.getLastReminderSentAt() != null
                        && w.getLastReminderSentAt().isAfter(java.time.LocalDateTime.now().minusDays(7)))
                .count();

        model.addAttribute("worklist", worklist);
        model.addAttribute("totalToCollect", totalToCollect);
        model.addAttribute("highPriorityCount", highPriorityCount);
        model.addAttribute("highPriorityAmount", highPriorityAmount);
        model.addAttribute("neverContactedCount", neverContactedCount);
        model.addAttribute("contactedRecentCount", contactedRecentCount);
        model.addAttribute("adminName", auth.getName());
        return "admin/collections";
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @PostMapping("/collections/remind")
    public String sendSingleReminder(@RequestParam String partyName,
                                     @RequestParam(required = false, defaultValue = "/admin/collections") String redirectTo,
                                     Authentication auth,
                                     RedirectAttributes redirectAttributes) {
        try {
            var result = notificationService.sendSingleReminder(partyName, triggeredByLabel(auth));
            if (result.getStatus() == com.empmgmt.entity.NotificationLog.Status.SENT) {
                redirectAttributes.addFlashAttribute("successMsg", "✅ Reminder sent to " + partyName);
            } else if (result.getStatus() == com.empmgmt.entity.NotificationLog.Status.DRY_RUN) {
                redirectAttributes.addFlashAttribute("successMsg", "🧪 Dry-run logged for " + partyName + " (whatsapp.mode=LOG_ONLY)");
            } else {
                redirectAttributes.addFlashAttribute("errorMsg", "❌ Send failed for " + partyName + ": " + result.getErrorMessage());
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        // Only ever redirect to a known in-app page — never trust an arbitrary URL from the client.
        boolean safe = redirectTo.equals("/admin/collections") || redirectTo.equals("/admin/payment-behavior");
        return "redirect:" + (safe ? redirectTo : "/admin/collections");
    }

    // ─── Party Payment Behavior ────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/payment-behavior")
    public String paymentBehavior(Model model, Authentication auth) {
        var behavior = invoiceService.getPartyPaymentBehavior();

        long reliableCount = behavior.stream()
                .filter(b -> b.getBehaviorLabel().equals("Paid Up") || b.getBehaviorLabel().equals("Regular"))
                .count();
        long slowCount = behavior.stream()
                .filter(b -> b.getBehaviorLabel().equals("Slow") || b.getBehaviorLabel().equals("Very Slow")
                        || b.getBehaviorLabel().equals("Chronic Late"))
                .count();
        java.util.List<Double> knownDelays = behavior.stream()
                .map(com.empmgmt.dto.PartyPaymentBehaviorDTO::getAvgDaysToPay)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        double companyAvgDays = knownDelays.isEmpty() ? 0.0
                : knownDelays.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        java.util.List<String> behaviorOrder = java.util.List.of(
                "Paid Up", "Regular", "New", "Slow", "Very Slow", "Chronic Late");
        java.util.Map<String, Long> countsByLabel = behavior.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.empmgmt.dto.PartyPaymentBehaviorDTO::getBehaviorLabel, java.util.stream.Collectors.counting()));
        java.util.List<Long> behaviorCounts = behaviorOrder.stream()
                .map(label -> countsByLabel.getOrDefault(label, 0L))
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("behavior", behavior);
        model.addAttribute("reliableCount", reliableCount);
        model.addAttribute("slowCount", slowCount);
        model.addAttribute("companyAvgDays", companyAvgDays);
        model.addAttribute("behaviorLabels", behaviorOrder);
        model.addAttribute("behaviorCounts", behaviorCounts);
        model.addAttribute("adminName", auth.getName());
        return "admin/payment-behavior";
    }

    /** "ADMIN:<username>" or "ACCOUNTANT:<username>" — used as the NotificationLog.triggeredBy label. */
    private String triggeredByLabel(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return (isAdmin ? "ADMIN:" : "ACCOUNTANT:") + auth.getName();
    }
}
