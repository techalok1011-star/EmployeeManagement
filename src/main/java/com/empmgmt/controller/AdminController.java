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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.time.LocalDate;
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
            return "admin/employees";
        }
        try {
            userService.createEmployee(request);
            redirectAttributes.addFlashAttribute("successMsg", "Employee created successfully!");
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

    @GetMapping("/entries")
    public String allEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            Model model) {
        boolean filtered = from != null || to != null || employeeId != null;
        model.addAttribute("entries", filtered
                ? paymentEntryService.getFilteredEntriesForAdmin(from, to, employeeId)
                : paymentEntryService.getAllEntries());
        model.addAttribute("employees", userService.getAllEmployees());
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

    @GetMapping("/invoices")
    public String invoices(Model model, Authentication auth) {
        model.addAttribute("invoices", invoiceService.getAllInvoices());
        model.addAttribute("outstandingSummary", invoiceService.getPartyOutstandingSummary());
        model.addAttribute("newInvoice", new InvoiceDTO.Request());
        invoiceService.getInvoicePageStats().forEach(model::addAttribute);
        model.addAttribute("recentNotifications", notificationLogRepository.findTop30ByOrderBySentAtDesc());
        model.addAttribute("adminName", auth.getName());
        return "admin/invoices";
    }

    @PostMapping("/invoices/add")
    public String addInvoice(@Valid @ModelAttribute("newInvoice") InvoiceDTO.Request request,
                             BindingResult result,
                             Model model,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("invoices", invoiceService.getAllInvoices());
            model.addAttribute("outstandingSummary", invoiceService.getPartyOutstandingSummary());
            invoiceService.getInvoicePageStats().forEach(model::addAttribute);
            model.addAttribute("recentNotifications", notificationLogRepository.findTop30ByOrderBySentAtDesc());
            model.addAttribute("adminName", auth.getName());
            return "admin/invoices";
        }
        try {
            invoiceService.createInvoice(request);
            redirectAttributes.addFlashAttribute("successMsg", "Invoice added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/invoices";
    }

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

    // ─── Party Management ─────────────────────────────────────

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

    @GetMapping("/parties/{id}/edit")
    public String editPartyForm(@PathVariable Long id, Model model, Authentication auth) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Party not found"));
        PartyDTO.Request req = PartyDTO.Request.builder()
                .name(party.getName())
                .gstin(party.getGst())
                .phone(party.getPhone())
                .whatsappOptIn(party.isWhatsappOptIn())
                .build();
        model.addAttribute("party", party);
        model.addAttribute("editRequest", req);
        model.addAttribute("adminName", auth.getName());
        return "admin/edit-party";
    }

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
            party.setPhone(request.getPhone() != null && !request.getPhone().isBlank()
                    ? request.getPhone().trim() : null);
            party.setWhatsappOptIn(request.isWhatsappOptIn());
            partyRepository.save(party);
        });
        redirectAttributes.addFlashAttribute("successMsg", "Party updated successfully!");
        return "redirect:/admin/parties";
    }

    @PostMapping("/parties/{id}/delete")
    public String deleteParty(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        partyRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMsg", "Party deleted.");
        return "redirect:/admin/parties";
    }

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

    @PostMapping("/notifications/send")
    public String sendNotificationsNow(Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            var logs = notificationService.sendDailyReminders("ADMIN:" + auth.getName());
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
}
