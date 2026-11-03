package com.empmgmt.util;

import java.util.List;

/**
 * One page of a larger list plus what the page bar needs. {@code page} is zero-based.
 * Used by the admin All Entries / Invoices lists, which used to load and draw every row ever saved.
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static final int DEFAULT_SIZE = 100;

    public int totalPages() {
        return (int) Math.max(1, (total + size - 1) / size);
    }

    /** 1-based number of the first row on this page (0 when empty). */
    public long firstRow() {
        return total == 0 ? 0 : (long) page * size + 1;
    }

    public long lastRow() {
        return Math.min(total, (long) (page + 1) * size);
    }

    /** Slices an already-loaded list (used for the rare numeric voucher sort, which needs every matching row). */
    public static <T> PageResult<T> slice(List<T> all, int page, int size) {
        int p = clampPage(page, all.size(), size);
        int from = Math.min(all.size(), p * size);
        int to = Math.min(all.size(), from + size);
        return new PageResult<>(List.copyOf(all.subList(from, to)), all.size(), p, size);
    }

    /** A page number typed or mangled by hand ("abc", "") becomes the first page instead of an error page. */
    public static int parsePage(String raw) {
        try { return Math.max(0, Integer.parseInt(raw == null ? "0" : raw.trim())); } catch (NumberFormatException e) { return 0; }
    }

    /** Keeps a requested page number inside 0..lastPage, so a stale link never shows an empty page. */
    public static int clampPage(int requested, long total, int size) {
        int last = (int) Math.max(0, (total + size - 1) / size - 1);
        return Math.max(0, Math.min(requested, last));
    }
}
