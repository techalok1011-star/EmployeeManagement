package com.empmgmt.controller;

import com.empmgmt.dto.PaymentEntryDTO;
import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.service.PaymentEntryService;
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

import java.time.LocalDate;

@Controller
@RequestMapping("/employee")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeController {

    private final PaymentEntryService paymentEntryService;
    private final UserService userService;

    // ─── Dashboard ────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        String username = auth.getName();
        model.addAttribute("user", userService.getUserByUsername(username));
        model.addAttribute("summary", paymentEntryService.getDailySummaryForEmployee(username));
        model.addAttribute("todayEntries", paymentEntryService.getTodayEntriesForEmployee(username));
        model.addAttribute("newEntry", new PaymentEntryDTO.Request());
        model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
        model.addAttribute("today", LocalDate.now());
        return "employee/dashboard";
    }

    // ─── Add Entry ────────────────────────────────────────────

    @PostMapping("/entries/add")
    public String addEntry(@Valid @ModelAttribute("newEntry") PaymentEntryDTO.Request request,
                           BindingResult result,
                           Authentication auth,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            String username = auth.getName();
            model.addAttribute("user", userService.getUserByUsername(username));
            model.addAttribute("summary", paymentEntryService.getDailySummaryForEmployee(username));
            model.addAttribute("todayEntries", paymentEntryService.getTodayEntriesForEmployee(username));
            model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
            model.addAttribute("today", LocalDate.now());
            return "employee/dashboard";
        }
        // Always force today's date — employees cannot add past/future entries
        request.setEntryDate(LocalDate.now());
        paymentEntryService.createEntry(request, auth.getName());
        redirectAttributes.addFlashAttribute("successMsg", "Entry added successfully!");
        return "redirect:/employee/dashboard";
    }

    // ─── View All Entries (day-wise) ──────────────────────────

    @GetMapping("/entries")
    public String allEntries(Model model, Authentication auth) {
        String username = auth.getName();
        model.addAttribute("user", userService.getUserByUsername(username));
        model.addAttribute("dayGroups", paymentEntryService.getEntriesGroupedByDayForEmployee(username));
        model.addAttribute("summary", paymentEntryService.getDailySummaryForEmployee(username));
        model.addAttribute("totalAllTime",
                paymentEntryService.getEntriesForEmployee(username).size());
        return "employee/entries";
    }

    // ─── Edit Entry ───────────────────────────────────────────

    @GetMapping("/entries/{id}/edit")
    public String editEntryForm(@PathVariable Long id, Authentication auth, Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            PaymentEntryDTO.Response entry = paymentEntryService.getEntryById(id);
            // Security: employees can only edit their own entries
            if (!entry.getEmployeeUsername().equals(auth.getName())) {
                redirectAttributes.addFlashAttribute("errorMsg", "You can only edit your own entries.");
                return "redirect:/employee/entries";
            }
            // Restriction: employees can only edit today's entries
            if (!entry.getEntryDate().equals(LocalDate.now())) {
                redirectAttributes.addFlashAttribute("errorMsg",
                        "You can only edit today's entries. Past entries are locked.");
                return "redirect:/employee/entries";
            }
            PaymentEntryDTO.Request req = new PaymentEntryDTO.Request();
            req.setPartyName(entry.getPartyName());
            req.setAmount(entry.getAmount());
            req.setRemarks(entry.getRemarks());
            req.setEntryDate(entry.getEntryDate());
            model.addAttribute("entry", entry);
            model.addAttribute("editRequest", req);
            model.addAttribute("paymentModes", PaymentEntry.ModeOfPayment.values());
            model.addAttribute("user", userService.getUserByUsername(auth.getName()));
            return "employee/edit-entry";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/employee/entries";
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
            return "employee/edit-entry";
        }
        try {
            // Employees cannot change the entry date — always keep it as today
            request.setEntryDate(LocalDate.now());
            paymentEntryService.updateEntryByEmployee(id, request, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Entry updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/employee/entries";
    }

    // ─── Delete Entry ─────────────────────────────────────────

    @PostMapping("/entries/{id}/delete")
    public String deleteEntry(@PathVariable Long id,
                              Authentication auth,
                              RedirectAttributes redirectAttributes) {
        try {
            paymentEntryService.deleteEntryByEmployee(id, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Entry deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/employee/entries";
    }

    // ─── Transaction History ──────────────────────────────────

    @GetMapping("/history")
    public String history(Model model, Authentication auth) {
        String username = auth.getName();
        model.addAttribute("user", userService.getUserByUsername(username));
        model.addAttribute("logs",
                paymentEntryService.getTransactionLogsForEmployee(username));
        return "employee/history";
    }
}
