package com.empmgmt.service;

import com.empmgmt.dto.PartyLedgerDTO;
import com.empmgmt.dto.PaymentEntryDTO;
import com.empmgmt.dto.TransactionLogDTO;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class ExportService {

    // ─────────────────────────────────────────────────────────
    // EXCEL — Payment Entries
    // ─────────────────────────────────────────────────────────

    public byte[] exportEntriesToExcel(List<PaymentEntryDTO.Response> entries) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Payment Entries");

            // ── Header style ──
            XSSFCellStyle headerStyle = wb.createCellStyle();
            XSSFFont headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(new XSSFColor(new byte[]{(byte)0xe2, (byte)0xe8, (byte)0xf0}, null));
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x1e, (byte)0x23, (byte)0x30}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);

            // ── Amount style (numeric, summable) ──
            DataFormat df = wb.createDataFormat();
            XSSFCellStyle amountStyle = wb.createCellStyle();
            amountStyle.setDataFormat(df.getFormat("#,##0.00"));
            amountStyle.setAlignment(HorizontalAlignment.RIGHT);

            // ── Date style ──
            XSSFCellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(df.getFormat("dd-mmm-yyyy"));
            dateStyle.setAlignment(HorizontalAlignment.CENTER);

            // ── Header row ──
            String[] headers = {"#", "Party Name", "Employee", "Amount (INR)", "Mode", "Entry Date",
                                 "Remarks", "Edited By", "Created At"};
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Data rows ──
            int rowNum = 1;
            for (PaymentEntryDTO.Response e : entries) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(rowNum - 1);
                row.createCell(1).setCellValue(e.getPartyName());
                row.createCell(2).setCellValue(e.getEmployeeName());

                Cell amtCell = row.createCell(3);
                amtCell.setCellValue(e.getAmount().doubleValue());
                amtCell.setCellStyle(amountStyle);

                row.createCell(4).setCellValue(e.getModeOfPayment());

                Cell dateCell = row.createCell(5);
                if (e.getEntryDate() != null) {
                    dateCell.setCellValue(java.sql.Date.valueOf(e.getEntryDate()));
                    dateCell.setCellStyle(dateStyle);
                }

                row.createCell(6).setCellValue(e.getRemarks() != null ? e.getRemarks() : "");
                row.createCell(7).setCellValue(e.getEditedBy() != null ? e.getEditedBy() : "");
                row.createCell(8).setCellValue(e.getCreatedAt() != null ? e.getCreatedAt() : "");
            }

            // ── SUM formula row ──
            if (!entries.isEmpty()) {
                int sumRow = rowNum + 1;
                Row totalsRow = sheet.createRow(sumRow);
                XSSFCellStyle totalLabelStyle = wb.createCellStyle();
                XSSFFont boldFont = wb.createFont();
                boldFont.setBold(true);
                totalLabelStyle.setFont(boldFont);

                Cell labelCell = totalsRow.createCell(1);
                labelCell.setCellValue("TOTAL");
                labelCell.setCellStyle(totalLabelStyle);

                Cell sumCell = totalsRow.createCell(3);
                sumCell.setCellFormula("SUM(D2:D" + rowNum + ")");
                sumCell.setCellStyle(amountStyle);
            }

            // ── Auto-size columns ──
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Add padding to auto-sized width (max 8000 chars wide)
                int width = sheet.getColumnWidth(i) + 512;
                sheet.setColumnWidth(i, Math.min(width, 15000));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────
    // PDF — Payment Entries
    // ─────────────────────────────────────────────────────────

    public byte[] exportEntriesToPdf(List<PaymentEntryDTO.Response> entries, LocalDate from, LocalDate to)
            throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 32, 32, 40, 32);
        PdfWriter.getInstance(doc, out);
        doc.open();

        // ── Title ──
        com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, Color.BLACK);
        Paragraph title = new Paragraph("PayTrack \u2014 Payment Entries Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        doc.add(title);

        // ── Subtitle ──
        String fromStr = from != null ? from.toString() : "beginning";
        String toStr   = to   != null ? to.toString()   : LocalDate.now().toString();
        com.lowagie.text.Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9,
                new Color(100, 100, 100));
        Paragraph subtitle = new Paragraph(
                "Period: " + fromStr + "  to  " + toStr + "   \u2022   Records: " + entries.size(), subFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(18);
        doc.add(subtitle);

        // ── Table ──
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4f, 22f, 15f, 12f, 12f, 12f, 23f});
        table.setSpacingBefore(6);
        table.setHeaderRows(1);

        Color headerBg = new Color(30, 35, 48);
        com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String h : new String[]{"#", "Party Name", "Employee", "Amount (INR)", "Mode", "Date", "Remarks"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(new Color(42, 48, 69));
            cell.setBorderWidth(0.5f);
            table.addCell(cell);
        }

        com.lowagie.text.Font dataFont  = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        com.lowagie.text.Font amtFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8,
                new Color(21, 128, 61));
        Color evenBg = Color.WHITE;
        Color oddBg  = new Color(248, 249, 252);

        for (int i = 0; i < entries.size(); i++) {
            PaymentEntryDTO.Response e = entries.get(i);
            Color rowBg = (i % 2 == 0) ? evenBg : oddBg;

            addPdfCell(table, String.valueOf(i + 1),             dataFont, rowBg, Element.ALIGN_CENTER);
            addPdfCell(table, e.getPartyName(),                  dataFont, rowBg, Element.ALIGN_LEFT);
            addPdfCell(table, e.getEmployeeName(),               dataFont, rowBg, Element.ALIGN_LEFT);
            addPdfCell(table, "\u20b9" + e.getAmount().toPlainString(), amtFont, rowBg, Element.ALIGN_RIGHT);
            addPdfCell(table, e.getModeOfPayment(),              dataFont, rowBg, Element.ALIGN_CENTER);
            addPdfCell(table, e.getEntryDate() != null ? e.getEntryDate().toString() : "",
                       dataFont, rowBg, Element.ALIGN_CENTER);
            addPdfCell(table, e.getRemarks() != null ? e.getRemarks() : "",
                       dataFont, rowBg, Element.ALIGN_LEFT);
        }

        doc.add(table);

        // ── Footer ──
        com.lowagie.text.Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7,
                new Color(150, 150, 150));
        Paragraph footer = new Paragraph(
                "Generated by PayTrack on " + LocalDate.now() + "   |   Total records: " + entries.size(),
                footerFont);
        footer.setAlignment(Element.ALIGN_RIGHT);
        footer.setSpacingBefore(14);
        doc.add(footer);

        doc.close();
        return out.toByteArray();
    }

    private void addPdfCell(PdfPTable table, String text, com.lowagie.text.Font font,
                            Color bg, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(new Color(220, 224, 235));
        cell.setBorderWidth(0.4f);
        table.addCell(cell);
    }

    // ─────────────────────────────────────────────────────────
    // CSV — Audit Log
    // ─────────────────────────────────────────────────────────

    public byte[] exportAuditLogToCsv(List<TransactionLogDTO.Response> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM — keeps Excel from mangling non-ASCII chars

        // Header
        sb.append("Action,Employee Name,Username,Party Name,Amount,Mode,Entry Date,")
          .append("Performed By,Changes / Notes,When\n");

        for (TransactionLogDTO.Response log : logs) {
            sb.append(csvField(log.getAction())).append(',');
            sb.append(csvField(log.getEmployeeName())).append(',');
            sb.append(csvField(log.getEmployeeUsername())).append(',');
            sb.append(csvField(log.getPartyName())).append(',');
            sb.append(log.getAmount() != null ? log.getAmount().toPlainString() : "").append(',');
            sb.append(csvField(log.getModeOfPayment())).append(',');
            sb.append(log.getEntryDate() != null ? log.getEntryDate().toString() : "").append(',');
            sb.append(csvField(log.getPerformedBy())).append(',');
            sb.append(csvField(log.getNotes())).append(',');
            sb.append(csvField(log.getPerformedAt()));
            sb.append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * RFC 4180 CSV field escaping: wrap in double quotes, escape internal quotes as "".
     * Returns empty string (unquoted) for null/blank.
     */
    private static String csvField(String value) {
        if (value == null || value.isEmpty()) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    // ─────────────────────────────────────────────────────────
    // PDF — Party Ledger (Statement of Account)
    // ─────────────────────────────────────────────────────────

    /**
     * @param entries already filtered to the requested date range by the caller - the
     *                running balance on each row still reflects the party's full history
     *                up to that date (computed once by {@code InvoiceService.getPartyLedger},
     *                not recomputed from zero within the filtered window).
     */
    public byte[] exportPartyLedgerToPdf(PartyLedgerDTO.Response ledger, List<PartyLedgerDTO.Entry> entries,
                                          LocalDate from, LocalDate to) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 40, 32);
        PdfWriter.getInstance(doc, out);
        doc.open();

        com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, Color.BLACK);
        Paragraph title = new Paragraph("PayTrack — Statement of Account: " + ledger.getDisplayName(), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        doc.add(title);

        String fromStr = from != null ? from.toString() : "beginning";
        String toStr   = to   != null ? to.toString()   : LocalDate.now().toString();
        com.lowagie.text.Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 100, 100));
        Paragraph subtitle = new Paragraph(
                "Period: " + fromStr + "  to  " + toStr + "   •   Transactions: " + entries.size(), subFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(4);
        doc.add(subtitle);

        BigDecimal periodDebit = entries.stream().map(PartyLedgerDTO.Entry::getDebit)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal periodCredit = entries.stream().map(PartyLedgerDTO.Entry::getCredit)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Paragraph totals = new Paragraph(
                "Period Invoiced: ₹" + periodDebit.toPlainString()
                        + "   •   Period Paid: ₹" + periodCredit.toPlainString(), subFont);
        totals.setAlignment(Element.ALIGN_CENTER);
        totals.setSpacingAfter(18);
        doc.add(totals);

        PdfPTable table = new PdfPTable(9);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{9f, 8f, 14f, 10f, 10f, 22f, 10f, 10f, 11f});
        table.setSpacingBefore(6);
        table.setHeaderRows(1);

        Color headerBg = new Color(30, 35, 48);
        com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String h : new String[]{"Date", "Type", "Reference", "Sales Vch", "Receipt Vch",
                                      "Description", "Debit", "Credit", "Balance"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(new Color(42, 48, 69));
            cell.setBorderWidth(0.5f);
            table.addCell(cell);
        }

        com.lowagie.text.Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        com.lowagie.text.Font debitFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(185, 28, 28));
        com.lowagie.text.Font creditFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(21, 128, 61));
        com.lowagie.text.Font balanceFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(30, 64, 175));
        Color evenBg = Color.WHITE;
        Color oddBg  = new Color(248, 249, 252);

        for (int i = 0; i < entries.size(); i++) {
            PartyLedgerDTO.Entry e = entries.get(i);
            Color rowBg = (i % 2 == 0) ? evenBg : oddBg;

            addPdfCell(table, e.getDate() != null ? e.getDate().toString() : "", dataFont, rowBg, Element.ALIGN_CENTER);
            addPdfCell(table, e.getType(), dataFont, rowBg, Element.ALIGN_CENTER);
            addPdfCell(table, e.getReference() != null ? e.getReference() : "", dataFont, rowBg, Element.ALIGN_LEFT);
            addPdfCell(table, e.getSalesVchNo() != null ? e.getSalesVchNo() : "", dataFont, rowBg, Element.ALIGN_CENTER);
            addPdfCell(table, e.getReceiptVchNo() != null ? e.getReceiptVchNo() : "", dataFont, rowBg, Element.ALIGN_CENTER);
            addPdfCell(table, e.getDescription() != null ? e.getDescription() : "", dataFont, rowBg, Element.ALIGN_LEFT);
            addPdfCell(table, e.getDebit() != null ? "₹" + e.getDebit().toPlainString() : "",
                    debitFont, rowBg, Element.ALIGN_RIGHT);
            addPdfCell(table, e.getCredit() != null ? "₹" + e.getCredit().toPlainString() : "",
                    creditFont, rowBg, Element.ALIGN_RIGHT);
            addPdfCell(table, "₹" + e.getBalance().toPlainString(), balanceFont, rowBg, Element.ALIGN_RIGHT);
        }

        doc.add(table);

        com.lowagie.text.Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(150, 150, 150));
        Paragraph footer = new Paragraph(
                "Generated by PayTrack on " + LocalDate.now() + "   |   Party: " + ledger.getDisplayName(),
                footerFont);
        footer.setAlignment(Element.ALIGN_RIGHT);
        footer.setSpacingBefore(14);
        doc.add(footer);

        doc.close();
        return out.toByteArray();
    }

    // ─────────────────────────────────────────────────────────
    // CSV — Party Ledger (Statement of Account)
    // ─────────────────────────────────────────────────────────

    public byte[] exportPartyLedgerToCsv(List<PartyLedgerDTO.Entry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append((char) 0xFEFF); // UTF-8 BOM - keeps Excel from mangling the currency symbol / non-ASCII chars

        sb.append("Date,Type,Reference,Sales Vch No,Receipt Vch No,Description,Debit,Credit,Balance\n");

        for (PartyLedgerDTO.Entry e : entries) {
            sb.append(e.getDate() != null ? e.getDate().toString() : "").append(',');
            sb.append(csvField(e.getType())).append(',');
            sb.append(csvField(e.getReference())).append(',');
            sb.append(csvField(e.getSalesVchNo())).append(',');
            sb.append(csvField(e.getReceiptVchNo())).append(',');
            sb.append(csvField(e.getDescription())).append(',');
            sb.append(e.getDebit() != null ? e.getDebit().toPlainString() : "").append(',');
            sb.append(e.getCredit() != null ? e.getCredit().toPlainString() : "").append(',');
            sb.append(e.getBalance() != null ? e.getBalance().toPlainString() : "");
            sb.append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
