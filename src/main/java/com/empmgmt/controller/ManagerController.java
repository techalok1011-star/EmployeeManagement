package com.empmgmt.controller;

import com.empmgmt.dto.InvoiceDTO;
import com.empmgmt.dto.PaymentEntryDTO;
import com.empmgmt.entity.Invoice;
import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.service.InvoiceService;
import com.empmgmt.service.PaymentEntryService;
import com.empmgmt.service.UserService;
import com.empmgmt.util.IstClock;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mobile-first area for the MANAGER role: add + view invoices AND payment
 * entries (collections), mirroring the EMPLOYEE dashboard's "add today's
 * stuff from your phone" pattern for both. Managers still cannot see
 * parties or anything under /admin/** - this controller is the entirety of
 * their access.
 */
@Controller
@RequestMapping("/manager")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class ManagerController {

    private final InvoiceService invoiceService;
    private final PaymentEntryService paymentEntryService;
    private final UserService userService;

    // ─── Dashboard ────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String date,
                             @RequestParam(required = false) String month,
                             Model model, Authentication auth) {
        String username = auth.getName();
        model.addAttribute("user", userService.getUserByUsername(username));
        addDashboardAttributes(model, username, parseDateOrToday(date), parseMonthOrCurrent(month));
        if (!model.containsAttribute("newInvoice")) {
            InvoiceDTO.Request newInvoice = new InvoiceDTO.Request();
            newInvoice.setInvoiceDate(IstClock.today());
            newInvoice.setInvoiceNumber(invoiceService.getNextInvoiceNumber());
            model.addAttribute("newInvoice", newInvoice);
        }
        if (!model.containsAttribute("newEntry")) {
            model.addAttribute("newEntry", new PaymentEntryDTO.Request());
        }
        model.addAttribute("deliveryModes", Invoice.DeliveryMode.values());
        model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
        model.addAttribute("today", IstClock.today());
        if (!model.containsAttribute("activeTab")) {
            model.addAttribute("activeTab", "invoices");
        }
        return "manager/dashboard";
    }

    private LocalDate parseDateOrToday(String date) {
        if (date == null || date.isBlank()) return IstClock.today();
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return IstClock.today();
        }
    }

    private YearMonth parseMonthOrCurrent(String month) {
        if (month == null || month.isBlank()) return YearMonth.from(IstClock.today());
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            return YearMonth.from(IstClock.today());
        }
    }

    private void addDashboardAttributes(Model model, String username, LocalDate selectedDate, YearMonth selectedMonth) {
        List<InvoiceDTO.Response> dayInvoices = invoiceService.getInvoicesCreatedByOnDate(username, selectedDate);
        BigDecimal dayTotal = dayInvoices.stream()
                .map(InvoiceDTO.Response::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("dayInvoices", dayInvoices);
        model.addAttribute("dayTotal", dayTotal);
        model.addAttribute("selectedDate", selectedDate);

        LocalDate monthStart = selectedMonth.atDay(1);
        LocalDate monthEnd = selectedMonth.atEndOfMonth();
        List<InvoiceDTO.Response> monthInvoices = invoiceService.getInvoicesCreatedByDateRange(username, monthStart, monthEnd);
        BigDecimal monthTotal = monthInvoices.stream()
                .map(InvoiceDTO.Response::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("monthInvoices", monthInvoices);
        model.addAttribute("monthTotal", monthTotal);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedMonthLabel", selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));

        List<PaymentEntryDTO.Response> dayPayments = paymentEntryService.getEntriesForEmployeeOnDate(username, selectedDate);
        BigDecimal dayPaymentTotal = dayPayments.stream()
                .map(PaymentEntryDTO.Response::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("dayPayments", dayPayments);
        model.addAttribute("dayPaymentTotal", dayPaymentTotal);

        List<PaymentEntryDTO.Response> monthPayments = paymentEntryService.getEntriesForEmployeeDateRange(username, monthStart, monthEnd);
        BigDecimal monthPaymentTotal = monthPayments.stream()
                .map(PaymentEntryDTO.Response::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("monthPayments", monthPayments);
        model.addAttribute("monthPaymentTotal", monthPaymentTotal);
    }

    // ─── Add Invoice ──────────────────────────────────────────

    @PostMapping("/invoices/add")
    public String addInvoice(@Valid @ModelAttribute("newInvoice") InvoiceDTO.Request request,
                             BindingResult result,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        String username = auth.getName();
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.newInvoice", result);
            redirectAttributes.addFlashAttribute("newInvoice", request);
            redirectAttributes.addFlashAttribute("activeTab", "invoices");
            return "redirect:/manager/dashboard";
        }
        try {
            invoiceService.createInvoice(request, username);
            redirectAttributes.addFlashAttribute("successMsg", "Invoice added successfully!");
            return dashboardRedirectFor(request.getInvoiceDate());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/manager/dashboard";
    }

    /**
     * Dashboard lists only show the selected day/month (default: today). After saving an entry
     * dated on another day, land on that day/month so it's visible instead of seeming to vanish.
     */
    private String dashboardRedirectFor(LocalDate entryDate) {
        if (entryDate == null || entryDate.equals(IstClock.today())) return "redirect:/manager/dashboard";
        return "redirect:/manager/dashboard?date=" + entryDate + "&month=" + YearMonth.from(entryDate);
    }

    // ─── View All My Invoices ───────────────────────────────────

    @GetMapping("/invoices")
    public String allInvoices(Model model, Authentication auth) {
        String username = auth.getName();
        model.addAttribute("user", userService.getUserByUsername(username));
        model.addAttribute("invoices", invoiceService.getInvoicesCreatedBy(username));
        return "manager/invoices";
    }

    private Invoice.DeliveryMode findDeliveryModeByDisplayName(String displayName) {
        if (displayName == null) return null;
        for (Invoice.DeliveryMode mode : Invoice.DeliveryMode.values()) {
            if (mode.getDisplayName().equals(displayName)) return mode;
        }
        return null;
    }

    private PaymentEntry.ModeOfPayment findModeOfPaymentByDisplayName(String displayName) {
        if (displayName == null) return null;
        for (PaymentEntry.ModeOfPayment mode : PaymentEntry.ModeOfPayment.values()) {
            if (mode.getDisplayName().equals(displayName)) return mode;
        }
        return null;
    }

    // ─── Edit Invoice (own invoices, any date) ──────────────────

    @GetMapping("/invoices/{id}/edit")
    public String editInvoiceForm(@PathVariable Long id, Authentication auth, Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            InvoiceDTO.Response invoice = invoiceService.getInvoiceById(id);
            if (!auth.getName().equals(invoice.getCreatedBy())) {
                redirectAttributes.addFlashAttribute("errorMsg", "You can only edit invoices you added yourself.");
                return "redirect:/manager/invoices";
            }
            InvoiceDTO.Request req = new InvoiceDTO.Request();
            req.setInvoiceNumber(invoice.getInvoiceNumber());
            req.setInvoiceDate(invoice.getInvoiceDate());
            req.setPartyName(invoice.getPartyName());
            req.setBags(invoice.getBags());
            req.setRatePerBag(invoice.getRatePerBag());
            req.setDescription(invoice.getDescription());
            req.setDeliveryMode(findDeliveryModeByDisplayName(invoice.getDeliveryMode()));
            req.setTransportNumber(invoice.getTransportNumber());
            model.addAttribute("invoice", invoice);
            if (!model.containsAttribute("editRequest")) {
                model.addAttribute("editRequest", req);
            }
            model.addAttribute("deliveryModes", Invoice.DeliveryMode.values());
            model.addAttribute("user", userService.getUserByUsername(auth.getName()));
            return "manager/edit-invoice";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/manager/invoices";
        }
    }

    @PostMapping("/invoices/{id}/edit")
    public String editInvoice(@PathVariable Long id,
                              @Valid @ModelAttribute("editRequest") InvoiceDTO.Request request,
                              BindingResult result,
                              Authentication auth,
                              RedirectAttributes redirectAttributes) {
        InvoiceDTO.Response existing = invoiceService.getInvoiceById(id);
        if (!auth.getName().equals(existing.getCreatedBy())) {
            redirectAttributes.addFlashAttribute("errorMsg", "You can only edit invoices you added yourself.");
            return "redirect:/manager/invoices";
        }
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.editRequest", result);
            redirectAttributes.addFlashAttribute("editRequest", request);
            return "redirect:/manager/invoices/" + id + "/edit";
        }
        try {
            invoiceService.updateInvoice(id, request, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Invoice updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/manager/invoices";
    }

    // ─── Delete Invoice (own invoices, any date) ────────────────

    @PostMapping("/invoices/{id}/delete")
    public String deleteInvoice(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            InvoiceDTO.Response existing = invoiceService.getInvoiceById(id);
            if (!auth.getName().equals(existing.getCreatedBy())) {
                redirectAttributes.addFlashAttribute("errorMsg", "You can only delete invoices you added yourself.");
                return "redirect:/manager/invoices";
            }
            invoiceService.deleteInvoice(id, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Invoice deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/manager/invoices";
    }

    // ─── Add Collection (payment entry) ────────────────────────

    @PostMapping("/entries/add")
    public String addEntry(@Valid @ModelAttribute("newEntry") PaymentEntryDTO.Request request,
                           BindingResult result,
                           @RequestParam(required = false) MultipartFile receiptPhoto,
                           @RequestParam(required = false) BigDecimal latitude,
                           @RequestParam(required = false) BigDecimal longitude,
                           Authentication auth,
                           RedirectAttributes redirectAttributes) {
        String username = auth.getName();
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.newEntry", result);
            redirectAttributes.addFlashAttribute("newEntry", request);
            redirectAttributes.addFlashAttribute("activeTab", "collections");
            return "redirect:/manager/dashboard";
        }
        PaymentEntryDTO.Response saved;
        try {
            saved = paymentEntryService.createEntryForManager(request, username);
        } catch (IllegalArgumentException e) {
            // e.g. party does not exist - show the message and keep what the manager typed
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            redirectAttributes.addFlashAttribute("newEntry", request);
            redirectAttributes.addFlashAttribute("activeTab", "collections");
            return "redirect:/manager/dashboard";
        }

        // Receipt photo is optional - a problem attaching it should never block the entry itself.
        if (receiptPhoto != null && !receiptPhoto.isEmpty()) {
            try {
                paymentEntryService.attachReceipt(saved.getId(), receiptPhoto.getBytes(),
                        receiptPhoto.getContentType(), latitude, longitude);
            } catch (Exception e) {
                log.warn("Receipt photo attach failed for entry id={}: {}", saved.getId(), e.getMessage());
            }
        }

        redirectAttributes.addFlashAttribute("successMsg", "Collection added successfully!");
        redirectAttributes.addFlashAttribute("activeTab", "collections");
        return dashboardRedirectFor(request.getEntryDate());
    }

    // ─── View All My Collections (day-wise, filterable) ────────

    @GetMapping("/entries")
    public String allEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model, Authentication auth) {
        String username = auth.getName();
        boolean filtered = from != null || to != null;
        model.addAttribute("user", userService.getUserByUsername(username));
        model.addAttribute("dayGroups", filtered
                ? paymentEntryService.getFilteredEntriesGroupedByDayForEmployee(username, from, to)
                : paymentEntryService.getEntriesGroupedByDayForEmployee(username));
        model.addAttribute("summary", paymentEntryService.getDailySummaryForEmployee(username));
        model.addAttribute("totalAllTime", paymentEntryService.getEntriesForEmployee(username).size());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "manager/entries";
    }

    // ─── Edit Collection (own entries, any date) ───────────────

    @GetMapping("/entries/{id}/edit")
    public String editEntryForm(@PathVariable Long id, Authentication auth, Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            PaymentEntryDTO.Response entry = paymentEntryService.getEntryById(id);
            if (!entry.getEmployeeUsername().equals(auth.getName())) {
                redirectAttributes.addFlashAttribute("errorMsg", "You can only edit your own collections.");
                return "redirect:/manager/entries";
            }
            PaymentEntryDTO.Request req = new PaymentEntryDTO.Request();
            req.setPartyName(entry.getPartyName());
            req.setAmount(entry.getAmount());
            req.setRemarks(entry.getRemarks());
            req.setEntryDate(entry.getEntryDate());
            req.setReceiptVchNo(entry.getReceiptVchNo());
            req.setModeOfPayment(findModeOfPaymentByDisplayName(entry.getModeOfPayment()));
            model.addAttribute("entry", entry);
            if (!model.containsAttribute("editRequest")) {
                model.addAttribute("editRequest", req);
            }
            model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
            model.addAttribute("user", userService.getUserByUsername(auth.getName()));
            return "manager/edit-entry";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/manager/entries";
        }
    }

    @PostMapping("/entries/{id}/edit")
    public String editEntry(@PathVariable Long id,
                            @Valid @ModelAttribute("editRequest") PaymentEntryDTO.Request request,
                            BindingResult result,
                            Authentication auth,
                            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.editRequest", result);
            redirectAttributes.addFlashAttribute("editRequest", request);
            return "redirect:/manager/entries/" + id + "/edit";
        }
        try {
            paymentEntryService.updateEntryByManager(id, request, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Collection updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/manager/entries";
    }

    // ─── Delete Collection ──────────────────────────────────────

    @PostMapping("/entries/{id}/delete")
    public String deleteEntry(@PathVariable Long id,
                              Authentication auth,
                              RedirectAttributes redirectAttributes) {
        try {
            paymentEntryService.deleteEntryByEmployee(id, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Collection deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/manager/entries";
    }
}
