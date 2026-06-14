package com.empmgmt.controller;

import com.empmgmt.service.ExcelPartyService;
import com.empmgmt.dto.PartySuggestionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.Map;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

	private final ExcelPartyService excelPartyService;

	@GetMapping
	public List<String> search(@RequestParam(name = "q", required = false) String q,
							   @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
		return excelPartyService.search(q, limit);
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
}


