package com.empmgmt.controller;

import com.empmgmt.repository.PartyRepository;
import com.empmgmt.service.ExcelPartyService;
import com.empmgmt.dto.PartySuggestionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

	private final ExcelPartyService excelPartyService;
	private final PartyRepository partyRepository;

	@GetMapping
	public List<String> search(@RequestParam(name = "q", required = false) String q,
							   @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
		return excelPartyService.search(q, limit);
	}

	/**
	 * Distinct party_name text actually used in invoices/payment_entries - covers orphaned names
	 * (no parties row) that {@link #suggest} can never find since it only searches the parties table.
	 * Backs the party-merge tool's "From" autocomplete.
	 */
	@GetMapping("/ledger-names/suggest")
	public List<String> suggestLedgerNames(@RequestParam(name = "q", required = false) String q,
										   @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
		if (q == null || q.isBlank()) return List.of();
		return partyRepository.findLedgerPartyNamesMatching(q.trim(), limit);
	}

	/** Used by entry forms to decide whether to show the "this party does not exist" popup. */
	@GetMapping("/exists")
	public Map<String, Object> exists(@RequestParam(name = "name") String name) {
		return Map.of("exists", excelPartyService.exists(name));
	}

	@GetMapping("/suggest")
	public List<PartySuggestionDTO> suggest(@RequestParam(name = "q", required = false) String q,
											@RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
		return excelPartyService.searchStructured(q, limit);
	}

	@PostMapping("/import")
	public Map<String, Object> importNow() {
		int added = excelPartyService.importFromExcel();
		return Map.of("added", added);
	}

	@PostMapping("/cleanup")
	public Map<String, Object> cleanupInvalid() {
		int deleted = excelPartyService.cleanupInvalidEntries();
		return Map.of("deleted", deleted);
	}

	/**
	 * Admin UI: upload an Excel file and import party names into DB.
	 * Redirects back to admin dashboard with result message.
	 */
	@PostMapping("/upload-import")
	public String uploadAndImport(@RequestParam("file") MultipartFile file,
								  RedirectAttributes redirectAttributes) {
		if (file.isEmpty()) {
			redirectAttributes.addFlashAttribute("importError", "Please select an Excel file to upload.");
			return "redirect:/admin/dashboard";
		}
		String filename = file.getOriginalFilename();
		if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
			redirectAttributes.addFlashAttribute("importError", "Only .xlsx or .xls files are supported.");
			return "redirect:/admin/dashboard";
		}
		try {
			int added = excelPartyService.importFromStream(file.getInputStream());
			redirectAttributes.addFlashAttribute("importSuccess",
				"✅ Import complete! " + added + " new parties added" + (added == 0 ? " (no new entries — all already exist)." : "."));
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("importError", "Import failed: " + e.getMessage());
		}
		return "redirect:/admin/dashboard";
	}
}


