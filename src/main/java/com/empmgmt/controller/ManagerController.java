package com.empmgmt.controller;

import com.empmgmt.dto.InvoiceDTO;
import com.empmgmt.entity.Invoice;
import com.empmgmt.service.InvoiceService;
import com.empmgmt.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mobile-first area for the MANAGER role: add + view invoices, mirroring the
 * EMPLOYEE dashboard's "add today's collections from your phone" pattern but
 * for invoices instead of payments. Managers cannot see payment entries,
 * parties, or anything under /admin/** — this is deliberately scoped to just
 * invoice entry/viewing.
 */
@Controller
@RequestMapping("/manager")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class ManagerController {

    private final InvoiceService invoiceService;
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
        model.addAttribute("deliveryModes", Invoice.DeliveryMode.values());
        model.addAttribute("today", LocalDate.now());
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
            model.addAttribute("deliveryModes", Invoice.DeliveryMode.values());
            model.addAttribute("today", LocalDate.now());
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
}
