package com.empmgmt.controller;

import com.empmgmt.dto.InvoiceDTO;
import com.empmgmt.dto.PaymentEntryDTO;
import com.empmgmt.entity.Invoice;
import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.service.InvoiceService;
import com.empmgmt.service.PaymentEntryService;
import com.empmgmt.service.UserService;
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
        InvoiceDTO.Request newInvoice = new InvoiceDTO.Request();
        newInvoice.setInvoiceDate(LocalDate.now());
        newInvoice.setInvoiceNumber(invoiceService.getNextInvoiceNumber());
        model.addAttribute("newInvoice", newInvoice);
        model.addAttribute("newEntry", new PaymentEntryDTO.Request());
        model.addAttribute("deliveryModes", Invoice.DeliveryMode.values());
        model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activeTab", "invoices");
        return "manager/dashboard";
    }

    private LocalDate parseDateOrToday(String date) {
        if (date == null || date.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private YearMonth parseMonthOrCurrent(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            return YearMonth.now();
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
                             Model model,
                             RedirectAttributes redirectAttributes) {
        String username = auth.getName();
        if (result.hasErrors()) {
            model.addAttribute("user", userService.getUserByUsername(username));
            addDashboardAttributes(model, username, LocalDate.now(), YearMonth.now());
            model.addAttribute("newEntry", new PaymentEntryDTO.Request());
            model.addAttribute("deliveryModes", Invoice.DeliveryMode.values());
            model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("activeTab", "invoices");
            return "manager/dashboard";
        }
        try {
            invoiceService.createInvoice(request, username);
            redirectAttributes.addFlashAttribute("successMsg", "Invoice added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/manager/dashboard";
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
            model.addAttribute("editRequest", req);
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
                              Model model,
                              RedirectAttributes redirectAttributes) {
        InvoiceDTO.Response existing = invoiceService.getInvoiceById(id);
        if (!auth.getName().equals(existing.getCreatedBy())) {
            redirectAttributes.addFlashAttribute("errorMsg", "You can only edit invoices you added yourself.");
            return "redirect:/manager/invoices";
        }
        if (result.hasErrors()) {
            model.addAttribute("invoice", existing);
            model.addAttribute("deliveryModes", Invoice.DeliveryMode.values());
            model.addAttribute("user", userService.getUserByUsername(auth.getName()));
            return "manager/edit-invoice";
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
                           Model model,
                           RedirectAttributes redirectAttributes) {
        String username = auth.getName();
        if (result.hasErrors()) {
            model.addAttribute("user", userService.getUserByUsername(username));
            addDashboardAttributes(model, username, LocalDate.now(), YearMonth.now());
            InvoiceDTO.Request newInvoice = new InvoiceDTO.Request();
            newInvoice.setInvoiceDate(LocalDate.now());
            newInvoice.setInvoiceNumber(invoiceService.getNextInvoiceNumber());
            model.addAttribute("newInvoice", newInvoice);
            model.addAttribute("deliveryModes", Invoice.DeliveryMode.values());
            model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("activeTab", "collections");
            return "manager/dashboard";
        }
        // Always force today's date - same rule as the employee collection flow.
        request.setEntryDate(LocalDate.now());
        PaymentEntryDTO.Response saved = paymentEntryService.createEntry(request, username);

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
        return "redirect:/manager/dashboard";
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

    // ─── Edit Collection (today's entries only) ────────────────

    @GetMapping("/entries/{id}/edit")
    public String editEntryForm(@PathVariable Long id, Authentication auth, Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            PaymentEntryDTO.Response entry = paymentEntryService.getEntryById(id);
            if (!entry.getEmployeeUsername().equals(auth.getName())) {
                redirectAttributes.addFlashAttribute("errorMsg", "You can only edit your own collections.");
                return "redirect:/manager/entries";
            }
            if (!entry.getEntryDate().equals(LocalDate.now())) {
                redirectAttributes.addFlashAttribute("errorMsg",
                        "You can only edit today's collections. Past entries are locked.");
                return "redirect:/manager/entries";
            }
            PaymentEntryDTO.Request req = new PaymentEntryDTO.Request();
            req.setPartyName(entry.getPartyName());
            req.setAmount(entry.getAmount());
            req.setRemarks(entry.getRemarks());
            req.setEntryDate(entry.getEntryDate());
            req.setReceiptVchNo(entry.getReceiptVchNo());
            model.addAttribute("entry", entry);
            model.addAttribute("editRequest", req);
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
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            PaymentEntryDTO.Response entry = paymentEntryService.getEntryById(id);
            model.addAttribute("entry", entry);
            model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
            model.addAttribute("user", userService.getUserByUsername(auth.getName()));
            return "manager/edit-entry";
        }
        try {
            paymentEntryService.updateEntryByEmployee(id, request, auth.getName());
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
