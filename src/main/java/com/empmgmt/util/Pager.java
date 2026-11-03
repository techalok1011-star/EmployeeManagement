package com.empmgmt.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the page bar for a {@link PageResult}: "Showing 1-100 of 7,632", Prev / numbers / Next. Every link is the
 * current URL with only {@code page} replaced, so all filters, the search text and the sort carry over untouched.
 */
public final class Pager {

    public record Link(String label, String url, boolean current, boolean disabled) {}

    public record View(String summary, List<Link> links, boolean needed) {}

    private Pager() {}

    public static View of(HttpServletRequest request, PageResult<?> pr) {
        String summary = pr.total() == 0
                ? "No rows"
                : String.format("Showing %,d–%,d of %,d", pr.firstRow(), pr.lastRow(), pr.total());
        int pages = pr.totalPages();
        if (pages <= 1) return new View(summary, List.of(), false);

        int cur = pr.page();
        List<Link> links = new ArrayList<>();
        links.add(new Link("‹ Prev", url(request, cur - 1), false, cur == 0));
        // a window of page numbers around the current one, always including the first and last
        int from = Math.max(0, cur - 2), to = Math.min(pages - 1, cur + 2);
        if (from > 0) {
            links.add(new Link("1", url(request, 0), false, false));
            if (from > 1) links.add(new Link("…", null, false, true));
        }
        for (int p = from; p <= to; p++) links.add(new Link(String.valueOf(p + 1), url(request, p), p == cur, false));
        if (to < pages - 1) {
            if (to < pages - 2) links.add(new Link("…", null, false, true));
            links.add(new Link(String.valueOf(pages), url(request, pages - 1), false, false));
        }
        links.add(new Link("Next ›", url(request, cur + 1), false, cur >= pages - 1));
        return new View(summary, links, true);
    }

    private static String url(HttpServletRequest request, int page) {
        return ServletUriComponentsBuilder.fromRequest(request).replaceQueryParam("page", Math.max(0, page)).build().toUriString();
    }
}
