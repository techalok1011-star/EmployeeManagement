package com.empmgmt.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PagerTest {

    private static MockHttpServletRequest request(String query) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/admin/entries");
        r.setScheme("http");
        r.setServerName("localhost");
        r.setServerPort(8080);
        r.setQueryString(query);
        if (query != null) {
            for (String kv : query.split("&")) {
                String[] p = kv.split("=", 2);
                r.addParameter(p[0], p.length > 1 ? p[1] : "");
            }
        }
        return r;
    }

    private static PageResult<Integer> page(long total, int page) {
        return new PageResult<>(List.of(), total, page, 100);
    }

    private static List<String> labels(Pager.View v) {
        return v.links().stream().map(Pager.Link::label).toList();
    }

    @Test
    void summaryReadsLikeAPerson() {
        assertEquals("Showing 101–200 of 7,632", Pager.of(request(null), page(7632, 1)).summary());
        assertEquals("Showing 7,601–7,632 of 7,632", Pager.of(request(null), page(7632, 76)).summary());
        assertEquals("No rows", Pager.of(request(null), page(0, 0)).summary());
    }

    @Test
    void singlePageNeedsNoBar() {
        Pager.View v = Pager.of(request(null), page(60, 0));
        assertFalse(v.needed());
        assertTrue(v.links().isEmpty());
        assertEquals("Showing 1–60 of 60", v.summary());
    }

    @Test
    void firstPageDisablesPrevAndMarksPageOneCurrent() {
        Pager.View v = Pager.of(request(null), page(7632, 0));
        assertTrue(v.needed());
        assertTrue(v.links().get(0).disabled(), "Prev is greyed out on the first page");
        assertEquals("‹ Prev", v.links().get(0).label());
        Pager.Link one = v.links().stream().filter(l -> l.label().equals("1")).findFirst().orElseThrow();
        assertTrue(one.current());
        assertFalse(v.links().get(v.links().size() - 1).disabled(), "Next is available");
    }

    @Test
    void lastPageDisablesNext() {
        Pager.View v = Pager.of(request(null), page(7632, 76));
        assertTrue(v.links().get(v.links().size() - 1).disabled());
        assertFalse(v.links().get(0).disabled());
    }

    @Test
    void middlePageShowsAWindowWithEllipsesAndTheEnds() {
        Pager.View v = Pager.of(request(null), page(7632, 40)); // page 41 of 77
        assertEquals(List.of("‹ Prev", "1", "…", "39", "40", "41", "42", "43", "…", "77", "Next ›"), labels(v));
        assertEquals("41", v.links().stream().filter(Pager.Link::current).findFirst().orElseThrow().label());
    }

    @Test
    void nearTheStartThereIsNoLeadingEllipsis() {
        assertEquals(List.of("‹ Prev", "1", "2", "3", "…", "77", "Next ›"), labels(Pager.of(request(null), page(7632, 0))));
        assertEquals(List.of("‹ Prev", "1", "2", "3", "4", "5", "…", "77", "Next ›"), labels(Pager.of(request(null), page(7632, 2))));
    }

    @Test
    void fewPagesAreAllListed() {
        assertEquals(List.of("‹ Prev", "1", "2", "3", "Next ›"), labels(Pager.of(request(null), page(250, 1))));
    }

    @Test
    void linksKeepEveryFilterAndOnlyChangeThePage() {
        Pager.View v = Pager.of(request("q=cash&from=2026-06-01&employeeId=5&all=true&page=2"), page(7632, 2));
        Pager.Link next = v.links().get(v.links().size() - 1);
        assertEquals("Next ›", next.label());
        assertTrue(next.url().contains("q=cash"), next.url());
        assertTrue(next.url().contains("from=2026-06-01"), next.url());
        assertTrue(next.url().contains("employeeId=5"), next.url());
        assertTrue(next.url().contains("all=true"), next.url());
        assertTrue(next.url().contains("page=3"), "Next from page index 2 goes to index 3: " + next.url());
        assertEquals(1, next.url().split("page=", -1).length - 1, "page appears exactly once, never duplicated");
    }

    @Test
    void ellipsisIsNotClickable() {
        Pager.View v = Pager.of(request(null), page(7632, 40));
        v.links().stream().filter(l -> l.label().equals("…")).forEach(l -> {
            assertTrue(l.disabled());
            assertNull(l.url());
        });
    }

    @Test
    void searchTextWithSpecialCharactersIsEncodedSafely() {
        Pager.View v = Pager.of(request("q=a%26b"), page(7632, 0));
        String url = v.links().get(v.links().size() - 1).url();
        assertTrue(url.contains("page=1"));
        assertFalse(url.contains("<") || url.contains("\""), "no raw markup characters reach the link");
    }
}
