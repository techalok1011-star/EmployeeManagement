package com.empmgmt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class VoucherAuditDTO {

    public enum Series { RECEIPT, SALES }

    /** One diary book of 100 pages: vch numbers rangeStart..rangeEnd. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Book {
        private int bookIndex;
        private long rangeStart;
        private long rangeEnd;
        private int usedCount;
        private int missingCount;
        private int acknowledgedCount;
        private List<Run> missingRuns;
        /** Missing numbers here that already have an acknowledgement covering them. */
        private List<Run> acknowledgedRuns;
        /** First not-yet-used page number in this book, or null if the book has no blank tail. */
        private Long notYetUsedFrom;
    }

    /** An inclusive run of voucher numbers, e.g. 1783-1784. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Run {
        private long start;
        private long end;
    }

    /** One transaction behind a voucher number - which entry it was found on. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Occurrence {
        /** The underlying PaymentEntry/Invoice id - lets the UI link straight to that exact record. */
        private Long id;
        private LocalDate date;
        private BigDecimal amount;
        private String partyName;
        private String rawValue;
        /** Payment mode (CASH, BANK_TRANSFER, ...) for receipts; null for sales. */
        private String mode;
    }

    /** Something needing a human look: a duplicate, an out-of-range jump, or unreadable text. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExceptionRow {
        /** DUPLICATE, SUSPECT, AMBIGUOUS, or UNPARSEABLE. Plain String, not an enum - see the
         *  enum/CHECK-constraint gotcha this project has hit before; this list is expected to grow. */
        private String type;
        /** Null for AMBIGUOUS/UNPARSEABLE, where no number could be trusted. */
        private Long vchNumber;
        /** Sum of all the entries behind this number - what a SPLIT receipt adds up to. */
        private BigDecimal total;
        private List<Occurrence> occurrences;
    }

    /** A recorded explanation for a range of missing numbers - a voided page, a spoiled receipt, etc. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Acknowledgement {
        private Long id;
        private long rangeStart;
        private long rangeEnd;
        /** VOIDED, SPOILED, TRANSFER, or EXPLAINED. */
        private String reasonCategory;
        private String note;
        private String acknowledgedBy;
        private LocalDateTime acknowledgedAt;
    }

    /** Form payload for POST /admin/voucher-audit/acknowledge. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AcknowledgeRequest {
        private Series series;
        private String financialYear;
        private Long rangeStart;
        private Long rangeEnd;
        private String reasonCategory;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeriesReport {
        private Series series;
        private String financialYear;
        private long highWaterMark;
        private int totalUsed;
        /** Missing and not yet acknowledged - the number that actually needs attention. */
        private int totalMissing;
        private int totalAcknowledged;
        private int totalDuplicateValues;
        /** Receipt numbers used for one party on one day in different modes (cash + bank): normal, listed for review only. */
        private int totalSplit;
        private int totalSuspect;
        private int totalAmbiguous;
        private int totalUnparseable;
        private List<Book> books;
        /** Every currently-active missing run, flattened across books, for the acknowledge worklist. */
        private List<Run> activeMissingRuns;
        private List<ExceptionRow> exceptions;
        /** Split receipts (one voucher, paid more than one way) - not exceptions, shown separately. */
        private List<ExceptionRow> splits;
        private List<Acknowledgement> acknowledgements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private VoucherAuditDTO.SeriesReport receipts;
        private VoucherAuditDTO.SeriesReport sales;
    }
}
