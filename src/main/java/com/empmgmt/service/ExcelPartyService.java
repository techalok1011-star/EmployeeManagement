package com.empmgmt.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.empmgmt.entity.Party;
import com.empmgmt.repository.PartyRepository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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

    public int importFromExcel() {
        int added = 0;
        try {
            Path p = Paths.get(excelPath);
            if (!Files.exists(p)) {
                // try relative to working dir
                p = Paths.get(System.getProperty("user.dir"), excelPath);
            }

            if (!Files.exists(p)) {
                log.warn("Party Excel file not found at {}. Party suggestions will be empty.", excelPath);
                return 0;
            }

            try (InputStream is = Files.newInputStream(p)) {
                Workbook wb = WorkbookFactory.create(is);
                Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
                if (sheet == null) {
                    log.warn("No sheets found in Excel file {}", p.toAbsolutePath());
                    return 0;
                }

                Iterator<Row> rows = sheet.iterator();
                if (!rows.hasNext()) return 0;

                Row header = rows.next();
                int partyIdx = -1, gstIdx = -1;
                DataFormatter fmt = new DataFormatter();
                for (int i = 0; i < header.getLastCellNum(); i++) {
                    Cell c = header.getCell(i);
                    if (c == null) continue;
                    String h = fmt.formatCellValue(c).toLowerCase(Locale.ROOT);
                    if (partyIdx == -1 && h.contains("party")) partyIdx = i;
                    if (gstIdx == -1 && (h.contains("gst") || h.contains("gstin") || h.contains("tax"))) gstIdx = i;
                }
                log.info("Excel column detection: partyIdx={}, gstIdx={}", partyIdx, gstIdx);
                
                // Log sample data from first few rows to help identify correct columns
                List<Row> sampleRows = new ArrayList<>();
                while (rows.hasNext() && sampleRows.size() < 3) {
                    sampleRows.add(rows.next());
                }
                log.info("Sample data from first {} rows:", sampleRows.size());
                for (int i = 0; i < Math.min(5, header.getLastCellNum()); i++) {
                    StringBuilder sb = new StringBuilder("  Column " + i + ": ");
                    for (Row sr : sampleRows) {
                        Cell c = sr.getCell(i);
                        sb.append("[").append(fmt.formatCellValue(c)).append("] ");
                    }
                    log.info(sb.toString());
                }
                
                // Reset rows iterator
                rows = sheet.iterator();
                rows.next(); // skip header again

                // fallback if not detected
                if (partyIdx == -1) partyIdx = 0;

                Set<String> set = new LinkedHashSet<>();
                while (rows.hasNext()) {
                    Row r = rows.next();
                    String party = partyIdx >= 0 ? fmt.formatCellValue(r.getCell(partyIdx)) : "";
                    String gst = (gstIdx >= 0) ? fmt.formatCellValue(r.getCell(gstIdx)) : "";
                    if (party == null) party = "";
                    party = party.trim();
                    gst = gst == null ? "" : gst.trim();
                    if (party.isEmpty()) continue;
                    
                    // Skip rows that are purely numeric (these are row numbers, not party names)
                    if (party.matches("^\\d+$")) {
                        continue;
                    }
                    
                    // Skip header markers and special entries
                    if (party.equals("#") || party.equalsIgnoreCase("PARTY-WISE SALES SUMMARY") || 
                        (party.startsWith("GSTIN:") && party.contains("Bhaduli"))) {
                        continue;
                    }
                    
                    String combined = gst.isEmpty() ? party : party + "_" + gst;
                    set.add(combined);
                }

                for (String combined : set) {
                    if (!partyRepository.existsByCombined(combined)) {
                        String name = combined;
                        String gst = "";
                        int idx = combined.lastIndexOf('_');
                        if (idx > 0 && idx < combined.length() - 1) {
                            name = combined.substring(0, idx);
                            gst = combined.substring(idx + 1);
                        }
                        Party pEnt = Party.builder().name(name).gst(gst).combined(combined).build();
                        partyRepository.save(pEnt);
                        added++;
                    }
                }

                log.info("Imported {} new party entries from {}", added, p.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Failed to read party Excel file {}: {}", excelPath, e.getMessage());
        }
        return added;
    }

    public List<String> search(String q, int limit) {
        List<String> results;
        if (q == null || q.isBlank()) {
            results = partyRepository.findAll().stream().map(Party::getCombined).limit(limit).collect(Collectors.toList());
        } else {
            results = partyRepository.findTop50ByCombinedContainingIgnoreCase(q).stream().map(Party::getCombined).limit(limit).collect(Collectors.toList());
        }
        return results;
    }

    /** Return structured suggestions (name, gst, combined) for UI consumption. */
    public List<com.empmgmt.dto.PartySuggestionDTO> searchStructured(String q, int limit) {
        List<Party> list;
        if (q == null || q.isBlank()) {
            list = partyRepository.findAll().stream().limit(limit).collect(Collectors.toList());
        } else {
            list = partyRepository.findTop50ByCombinedContainingIgnoreCase(q).stream().limit(limit).collect(Collectors.toList());
        }
        return list.stream().map(p -> new com.empmgmt.dto.PartySuggestionDTO(p.getName(), p.getGst(), p.getCombined())).collect(Collectors.toList());
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


