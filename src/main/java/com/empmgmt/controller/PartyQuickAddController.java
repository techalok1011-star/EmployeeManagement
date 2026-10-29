package com.empmgmt.controller;

import com.empmgmt.dto.PartyDTO;
import com.empmgmt.entity.Party;
import com.empmgmt.service.PartyCreationService;
import com.empmgmt.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * "Add a new party" page open to every logged-in role. An entry form sends the user here when
 * they typed a party that doesn't exist; after saving, the user is returned to the page they came
 * from (the form's own script restores what they had typed and fills in the new party name).
 * Opening balance is deliberately not offered here - that stays an admin/accountant task.
 */
@Controller
@RequestMapping("/parties/new")
@RequiredArgsConstructor
@Slf4j
public class PartyQuickAddController {

    private final PartyCreationService partyCreationService;
    private final UserService userService;

    @GetMapping
    public String form(@RequestParam(required = false) String name,
                       @RequestParam(required = false) String returnTo,
                       Model model, Authentication auth) {
        if (!model.containsAttribute("newParty")) {
            PartyDTO.Request req = new PartyDTO.Request();
            req.setName(name != null ? name.trim() : "");
            model.addAttribute("newParty", req);
        }
        model.addAttribute("returnTo", safeReturnTo(returnTo, auth));
        model.addAttribute("user", userService.getUserByUsername(auth.getName()));
        return "party-quick-add";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newParty") PartyDTO.Request request,
                         BindingResult result,
                         @RequestParam(required = false) String returnTo,
                         Authentication auth,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        String back = safeReturnTo(returnTo, auth);
        if (result.hasErrors()) {
            model.addAttribute("returnTo", back);
            model.addAttribute("user", userService.getUserByUsername(auth.getName()));
            return "party-quick-add";
        }
        // Not allowed from here: opening balance is admin/accountant territory.
        request.setOpeningBalance(null);
        request.setOpeningBalanceDate(null);
        try {
            Party saved = partyCreationService.create(request, auth.getName());
            redirectAttributes.addFlashAttribute("successMsg",
                    "Party '" + saved.getCombined() + "' added. Continue with your entry.");
            String sep = back.contains("?") ? "&" : "?";
            return "redirect:" + back + sep + "createdParty="
                    + URLEncoder.encode(saved.getCombined(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
            model.addAttribute("returnTo", back);
            model.addAttribute("user", userService.getUserByUsername(auth.getName()));
            return "party-quick-add";
        }
    }

    /** Only same-site relative paths - never an open redirect. Falls back to the role's home page. */
    private String safeReturnTo(String returnTo, Authentication auth) {
        if (returnTo != null && returnTo.startsWith("/") && !returnTo.startsWith("//")
                && !returnTo.contains("\\") && !returnTo.contains("://")) {
            return returnTo;
        }
        boolean manager = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        boolean employee = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"));
        return manager ? "/manager/dashboard" : employee ? "/employee/dashboard" : "/admin/invoices";
    }
}
