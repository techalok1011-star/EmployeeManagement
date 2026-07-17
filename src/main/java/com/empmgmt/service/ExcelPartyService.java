package com.empmgmt.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.CellType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.empmgmt.entity.Party;
import com.empmgmt.repository.PartyRepository;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExcelPartyService {

    @Value("${party.excel.path:Party_wise_Sales_Summary.xlsx}")
    private String excelPath;
    
    @Value("${party.import.on-startup:false}")
    private boolean importOnStartup;

    private final PartyRepository partyRepository;

    @PostConstruct
    public void init() {
        if (importOnStartup) {
            importFromExcel();
        } else {
            log.info("Party Excel import on startup is disabled (party.import.on-startup=false). Using DB values for suggestions.");
        }
    }

    /**
     * Import from the configured file path on disk (used on startup or via /api/parties/import).
     */
    public int importFromExcel() {
        try {
            Path p = Paths.get(excelPath);
            if (!Files.exists(p)) {
                p = Paths.get(System.getProperty("user.dir"), excelPath);
            }
            if (!Files.exists(p)) {
                log.warn("Party Excel file not found at: {}. Upload via Admin UI instead.", p.toAbsolutePath());
                return 0;
            }
            try (InputStream is = Files.newInputStream(p)) {
                int added = importFromStream(is);
                log.info("✅ File import complete: {} new parties added from '{}'", added, p.toAbsolutePath());
                return added;
            }
        } catch (Exception e) {
            log.error("Failed to import parties from Excel file: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Import from an uploaded InputStream (used by Admin Upload API).
     * Scans ALL sheets, auto-detects "Party Name" + GSTIN columns.
     * Skips duplicates — safe to call multiple times.
     *
     * @return number of new parties inserted
     */
    public int importFromStream(InputStream is) {
        int added = 0;
        int updated = 0;
        try {
            // combined -> [gst, totalAmount]
            Map<String, String>     combinedToGst    = new LinkedHashMap<>();
            Map<String, BigDecimal> combinedToAmount = new LinkedHashMap<>();
            DataFormatter fmt = new DataFormatter();

            try (Workbook wb = WorkbookFactory.create(is)) {
                for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                    Sheet sheet = wb.getSheetAt(si);
                    log.info("Scanning sheet [{}]: '{}'", si, sheet.getSheetName());

                    int headerRowIdx = -1, partyColIdx = -1, gstColIdx = -1, amountColIdx = -1;

                    // Find header row containing "party name"
                    for (Row row : sheet) {
                        if (row == null) continue;
                        for (Cell c : row) {
                            String val = fmt.formatCellValue(c).toLowerCase(Locale.ROOT).trim();
                            if (val.contains("party") && val.contains("name")) {
                                headerRowIdx = row.getRowNum();
                                partyColIdx  = c.getColumnIndex();
                                break;
                            }
                        }
                        if (headerRowIdx >= 0) break;
                    }

                    if (partyColIdx < 0) {
                        log.info("  No 'Party Name' column in sheet '{}', skipping.", sheet.getSheetName());
                        continue;
                    }

                    // Find GSTIN and Amount columns in same header row
                    Row headerRow = sheet.getRow(headerRowIdx);
                    for (Cell c : headerRow) {
                        String val = fmt.formatCellValue(c).toLowerCase(Locale.ROOT).trim();
                        if (gstColIdx < 0 && (val.equals("gstin") || val.contains("buyer gst") ||
                            (val.contains("gst") && !val.contains("total") && !val.contains("%")))) {
                            gstColIdx = c.getColumnIndex();
                        }
                        // Match "total sales", "total amount", "amount" columns
                        if (amountColIdx < 0 && (val.contains("total sales") || val.contains("total amount") ||
                            (val.contains("amount") && !val.contains("party")))) {
                            amountColIdx = c.getColumnIndex();
                        }
                    }

                    log.info("  Sheet '{}': partyCol={}, gstCol={}, amountCol={}",
                            sheet.getSheetName(), partyColIdx, gstColIdx, amountColIdx);

                    // Read data rows — accumulate amounts per party (sum for invoice-detail sheets)
                    for (int ri = headerRowIdx + 1; ri <= sheet.getLastRowNum(); ri++) {
                        Row row = sheet.getRow(ri);
                        if (row == null) continue;

                        String name = fmt.formatCellValue(row.getCell(partyColIdx)).trim();
                        String gst  = gstColIdx >= 0 ? fmt.formatCellValue(row.getCell(gstColIdx)).trim() : "";

                        if (name.isEmpty() || name.matches("^\\d+$") || name.equalsIgnoreCase("Party Name")) continue;
                        // Guards against column-detection picking up the wrong sheet column (e.g. an
                        // "Invoice No." column like "GST0009") and importing it as a party name.
                        if (name.matches("(?i)^[A-Z]{2,6}\\d{2,8}$")) {
                            log.warn("  Skipping suspicious 'party name' that looks like a document/invoice number: {}", name);
                            continue;
                        }

                        String combined = gst.isEmpty() ? name : name + "_" + gst;
                        combinedToGst.put(combined, gst);

                        // Parse amount and accumulate (handles both summary and invoice-detail sheets)
                        if (amountColIdx >= 0) {
                            Cell amtCell = row.getCell(amountColIdx);
                            if (amtCell != null) {
                                try {
                                    BigDecimal amt;
                                    if (amtCell.getCellType() == CellType.NUMERIC) {
                                        amt = BigDecimal.valueOf(amtCell.getNumericCellValue());
                                    } else {
                                        String raw = fmt.formatCellValue(amtCell).replaceAll("[^\\d.]", "").trim();
                                        amt = raw.isEmpty() ? BigDecimal.ZERO : new BigDecimal(raw);
                                    }
                                    combinedToAmount.merge(combined, amt, BigDecimal::add);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }

                    log.info("  {} unique parties collected so far.", combinedToGst.size());
                }
            }

            // Persist — insert new, update amount if already exists
            for (Map.Entry<String, String> entry : combinedToGst.entrySet()) {
                String combined = entry.getKey();
                String gst      = entry.getValue();
                BigDecimal amount = combinedToAmount.getOrDefault(combined, null);

                var existing = partyRepository.findByCombined(combined);
                if (existing.isEmpty()) {
                    String name = gst.isEmpty() ? combined : combined.substring(0, combined.lastIndexOf('_'));
                    partyRepository.save(Party.builder()
                            .name(name).gst(gst).combined(combined).totalAmount(amount).build());
                    added++;
                } else {
                    // Update amount even if party already exists
                    Party p = existing.get();
                    if (amount != null) {
                        p.setTotalAmount(amount);
                        partyRepository.save(p);
                        updated++;
                    }
                }
            }

            log.info("✅ Import complete: {} new parties added, {} updated with amount.", added, updated);

        } catch (Exception e) {
            log.error("Failed to import parties from stream: {}", e.getMessage(), e);
        }
        return added;
    }

    public List<String> search(String q, int limit) {
        List<String> results;
        if (q == null || q.isBlank()) {
            results = partyRepository.findAll().stream().map(Party::getCombined).limit(limit).collect(Collectors.toList());
        } else {
            results = partyRepository.findTop50ByNameContainingIgnoreCaseOrTrailingNumberContainingIgnoreCase(q, q)
                    .stream().map(Party::getCombined).limit(limit).collect(Collectors.toList());
        }
        return results;
    }

    /**
     * Structured suggestions (name, gst, combined, trailingNumber) for UI autocomplete.
     * Matches against party name OR trailing number only — deliberately NOT against GST,
     * so a GST substring never surfaces an unrelated party by accident.
     */
    public List<com.empmgmt.dto.PartySuggestionDTO> searchStructured(String q, int limit) {
        List<Party> list;
        if (q == null || q.isBlank()) {
            list = partyRepository.findAll().stream().limit(limit).collect(Collectors.toList());
        } else {
            list = partyRepository.findTop50ByNameContainingIgnoreCaseOrTrailingNumberContainingIgnoreCase(q, q)
                    .stream().limit(limit).collect(Collectors.toList());
        }
        return list.stream()
                .map(p -> new com.empmgmt.dto.PartySuggestionDTO(p.getName(), p.getGst(), p.getCombined(), p.getTrailingNumber()))
                .collect(Collectors.toList());
    }

    /** Ensure a party record exists for the given combined string (name or name_gstin). */
    public void ensureExists(String combined) {
        if (combined == null || combined.isBlank()) return;
        String trimmed = combined.trim();
        if (partyRepository.existsByCombined(trimmed)) return;
        String name = trimmed;
        String gst = "";
        int idx = trimmed.lastIndexOf('_');
        if (idx > 0 && idx < trimmed.length() - 1) {
            name = trimmed.substring(0, idx);
            gst = trimmed.substring(idx + 1);
        }
        Party pEnt = Party.builder().name(name).gst(gst).combined(trimmed).build();
        try {
            partyRepository.save(pEnt);
        } catch (Exception e) {
            // ignore race conditions or unique constraint violations
            log.debug("Could not insert party {}: {}", trimmed, e.getMessage());
        }
    }

    /** Remove invalid entries like numeric rows and header text from Excel import */
    public int cleanupInvalidEntries() {
        List<Party> allParties = partyRepository.findAll();
        List<Party> toDelete = new ArrayList<>();
        
        for (Party p : allParties) {
            String combined = p.getCombined();
            if (combined == null) {
                toDelete.add(p);
                continue;
            }
            
            // Remove purely numeric entries (1, 2, 3, etc. from row numbers)
            if (combined.matches("^\\d+$")) {
                toDelete.add(p);
                continue;
            }
            
            // Remove special characters and headers
            if (combined.equals("#") || 
                combined.equalsIgnoreCase("PARTY-WISE SALES SUMMARY") ||
                combined.startsWith("GSTIN:") && combined.contains("Bhaduli")) {
                toDelete.add(p);
                continue;
            }
        }
        
        if (!toDelete.isEmpty()) {
            partyRepository.deleteAll(toDelete);
            log.info("Cleaned up {} invalid party entries from database", toDelete.size());
        }
        
        return toDelete.size();
    }
}


