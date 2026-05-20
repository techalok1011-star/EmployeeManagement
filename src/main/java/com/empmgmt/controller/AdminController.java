package com.empmgmt.controller;

import com.empmgmt.dto.PaymentEntryDTO;
import com.empmgmt.dto.UserDTO;
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

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final PaymentEntryService paymentEntryService;
    private final UserService userService;

    // ─── Dashboard ────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        var employees = userService.getAllEmployees();
        model.addAttribute("employees", employees);
        model.addAttribute("activeEmployeeCount",
                employees.stream().filter(e -> e.isActive()).count());
        model.addAttribute("recentEntries", paymentEntryService.getAllEntries());
        model.addAttribute("adminName", auth.getName());
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
    public String allEntries(Model model) {
        model.addAttribute("entries", paymentEntryService.getAllEntries());
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

    // ─── Transaction History (Audit Log) ──────────────────────

    @GetMapping("/history")
    public String history(Model model, Authentication auth) {
        model.addAttribute("logs", paymentEntryService.getAllTransactionLogs());
        model.addAttribute("adminName", auth.getName());
        return "admin/history";
    }
}
