package com.empmgmt.service;

import com.empmgmt.entity.Party;
import com.empmgmt.repository.InvoiceRepository;
import com.empmgmt.repository.PartyRepository;
import com.empmgmt.repository.PaymentEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

import static com.empmgmt.config.CacheConfig.*;

/**
 * Renames a party safely. Invoices and payments store party_name as free text equal to
 * parties.combined, with no FK - so changing only the party row orphans its whole history
 * (ledgers, reports and reminders drop names with no live party row). This changes the party row
 * and re-points every invoice and payment in one transaction, with one audit_logs row.
 * Only the name changes: GST, trailing number, phone and opening balance are left alone.
 * transaction_logs is left untouched on purpose - it is an immutable point-in-time audit snapshot.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PartyRenameService {

    private final PartyRepository partyRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentEntryRepository paymentEntryRepository;
    private final AuditLogService auditLogService;
    private final UserService userService;

    public record Result(String oldCombined, String newCombined, long invoices, long payments) {}

    @Transactional(readOnly = true)
    public long invoiceCount(Party p) { return invoiceRepository.countByPartyName(p.getCombined()); }

    @Transactional(readOnly = true)
    public long paymentCount(Party p) { return paymentEntryRepository.countByPartyName(p.getCombined()); }

    /**
     * The admin/accountant "Edit Party" save. Applies every field of the form like before, but when the name
     * or GST changes the party's combined name, its invoices and payments are re-pointed in the same
     * transaction - previously only the party row changed, which orphaned the whole history (KAIF, 2026-08).
     */
    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS, VOUCHER_AUDIT}, allEntries = true)
    public Result updateParty(Long partyId, com.empmgmt.dto.PartyDTO.Request req, String performedBy) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NoSuchElementException("Party not found"));

        String name = req.getName().trim();
        String gstin = req.getGstin() != null ? req.getGstin().trim() : "";
        String newCombined = gstin.isEmpty() ? name : name + "_" + gstin;
        String oldCombined = party.getCombined();
        boolean renamed = !newCombined.equals(oldCombined);

        long invoices = 0, payments = 0;
        if (renamed) {
            if (newCombined.length() > 700) throw new IllegalArgumentException("Party name is too long.");
            if (partyRepository.existsByCombined(newCombined)) {
                throw new IllegalArgumentException("Party '" + newCombined + "' already exists.");
            }
            long clash = invoiceRepository.countByPartyName(newCombined) + paymentEntryRepository.countByPartyName(newCombined);
            if (clash > 0) {
                throw new IllegalArgumentException("There are already " + clash + " invoices/payments filed under '" +
                        newCombined + "'. Use Merge Parties to bring them into this party instead of renaming.");
            }
            invoices = invoiceRepository.countByPartyName(oldCombined);
            payments = paymentEntryRepository.countByPartyName(oldCombined);
        }

        party.setName(name);
        party.setGst(gstin);
        party.setCombined(newCombined);
        party.setTrailingNumber(req.getTrailingNumber() != null && !req.getTrailingNumber().isBlank() ? req.getTrailingNumber().trim() : null);
        party.setPhone(req.getPhone() != null && !req.getPhone().isBlank() ? req.getPhone().trim() : null);
        party.setWhatsappOptIn(req.isWhatsappOptIn());
        party.setOpeningBalance(req.getOpeningBalance() != null ? req.getOpeningBalance() : java.math.BigDecimal.ZERO);
        party.setOpeningBalanceDate(req.getOpeningBalanceDate());
        partyRepository.save(party);

        if (renamed) {
            invoiceRepository.repointPartyName(oldCombined, newCombined);
            paymentEntryRepository.repointPartyName(oldCombined, newCombined);
        }

        String description = renamed
                ? String.format("Party '%s' updated to '%s' (%d invoice(s) and %d payment(s) re-pointed)", oldCombined, newCombined, invoices, payments)
                : "Party '" + oldCombined + "' updated to '" + newCombined + "'";
        String role = userService.getUserByUsername(performedBy).getRole();
        auditLogService.log("UPDATE", "PARTY", party.getId(), newCombined, null, description, performedBy, role);
        return new Result(oldCombined, newCombined, invoices, payments);
    }

    @CacheEvict(cacheNames = {PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER, AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS, VOUCHER_AUDIT}, allEntries = true)
    public Result rename(Long partyId, String newNameRaw, String performedBy) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NoSuchElementException("Party not found"));

        String newName = newNameRaw == null ? "" : newNameRaw.trim();
        if (newName.isEmpty()) {
            throw new IllegalArgumentException("Party name cannot be empty.");
        }
        String gst = party.getGst() == null ? "" : party.getGst().trim();
        String newCombined = gst.isEmpty() ? newName : newName + "_" + gst;
        if (newCombined.length() > 700) {
            throw new IllegalArgumentException("Party name is too long.");
        }

        String oldCombined = party.getCombined();
        if (newCombined.equals(oldCombined)) {
            throw new IllegalArgumentException("The name is unchanged - nothing to rename.");
        }
        if (partyRepository.existsByCombined(newCombined)) {
            throw new IllegalArgumentException("A party named '" + newCombined + "' already exists. " +
                    "If it is the same party, ask the admin to merge them.");
        }
        // History already filed under the new text (an orphaned name) would be pulled in silently.
        long clash = invoiceRepository.countByPartyName(newCombined) + paymentEntryRepository.countByPartyName(newCombined);
        if (clash > 0) {
            throw new IllegalArgumentException("There are already " + clash + " invoices/payments filed under '" +
                    newCombined + "'. Ask the admin to merge them instead of renaming.");
        }

        long invoices = invoiceRepository.countByPartyName(oldCombined);
        long payments = paymentEntryRepository.countByPartyName(oldCombined);

        party.setName(newName);
        party.setCombined(newCombined);
        partyRepository.save(party);
        invoiceRepository.repointPartyName(oldCombined, newCombined);
        paymentEntryRepository.repointPartyName(oldCombined, newCombined);

        String description = String.format("Party renamed '%s' -> '%s': %d invoice(s) and %d payment(s) re-pointed.",
                oldCombined, newCombined, invoices, payments);
        String role = userService.getUserByUsername(performedBy).getRole();
        auditLogService.log("RENAME", "PARTY", party.getId(), newCombined, null, description, performedBy, role);
        log.info("Party rename by {}: '{}' -> '{}' ({} invoices, {} payments)", performedBy, oldCombined, newCombined, invoices, payments);
        return new Result(oldCombined, newCombined, invoices, payments);
    }
}
