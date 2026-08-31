package com.empmgmt.util;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a trustworthy numeric value from a Tally voucher number field (receipt_vch_no /
 * sales_vch_no), which in practice is free text: zero-padded ("004"), trailing junk ("2463 T/F",
 * "2010."), or not a number at all ("T/F", "BHADA", blank). Shared by VoucherAuditService (gap/
 * duplicate detection) and the numeric-aware sort on the entries/invoices Vch No. columns, so both
 * agree on exactly which strings are trustworthy - see the AMBIGUOUS case below, which is the one
 * genuinely dangerous shape.
 */
public final class VoucherNumberParser {

    public enum Status { NUMERIC, BLANK, AMBIGUOUS, UNPARSEABLE }

    public record Result(Status status, Long value) {}

    /** Leading digits, ignoring leading zeros (so "004" and "4" are the same voucher number). */
    private static final Pattern LEADING_DIGITS = Pattern.compile("^\\s*0*([0-9]+)");

    private VoucherNumberParser() {}

    public static Result parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return new Result(Status.BLANK, null);
        }
        Matcher m = LEADING_DIGITS.matcher(trimmed);
        if (!m.find()) {
            return new Result(Status.UNPARSEABLE, null);
        }
        // A digit appearing again after the leading run - e.g. "25o2" (a letter O standing in for
        // a zero) - means the leading digits alone can't be trusted as the real number. Guessing
        // "25" here would silently invent a wrong voucher; better to flag it for a human to check
        // against the paper record than report a confident wrong answer.
        String remainder = trimmed.substring(m.end());
        if (remainder.chars().anyMatch(Character::isDigit)) {
            return new Result(Status.AMBIGUOUS, null);
        }
        try {
            return new Result(Status.NUMERIC, Long.parseLong(m.group(1)));
        } catch (NumberFormatException e) {
            return new Result(Status.UNPARSEABLE, null);
        }
    }

    /** Convenience for sorting/filtering: the numeric value only if confidently parseable. */
    public static Optional<Long> extractNumber(String raw) {
        Result r = parse(raw);
        return r.status() == Status.NUMERIC ? Optional.of(r.value()) : Optional.empty();
    }

    /**
     * A comparator for sorting a list by voucher number where the field isn't always a clean
     * integer. Rows with no trustworthy number always sort last, in either direction, rather than
     * flipping to the top on a descending sort - "no vch" isn't a value to rank, just a gap.
     */
    public static <T> Comparator<T> comparator(Function<T, String> vchExtractor, boolean ascending) {
        Comparator<T> hasNumberFirst = Comparator.comparing(item -> extractNumber(vchExtractor.apply(item)).isEmpty());
        Comparator<Long> valueOrder = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<T> byValue = Comparator.comparing(item -> extractNumber(vchExtractor.apply(item)).orElse(0L), valueOrder);
        return hasNumberFirst.thenComparing(byValue);
    }
}
