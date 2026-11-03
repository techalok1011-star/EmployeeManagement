package com.empmgmt.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    private static List<Integer> numbers(int n) {
        return new ArrayList<>(IntStream.rangeClosed(1, n).boxed().toList());
    }

    @Test
    void totalPagesRoundsUpAndIsNeverZero() {
        assertEquals(1, new PageResult<>(List.of(), 0, 0, 100).totalPages(), "an empty list still has one (empty) page");
        assertEquals(1, new PageResult<>(List.of(), 100, 0, 100).totalPages());
        assertEquals(2, new PageResult<>(List.of(), 101, 0, 100).totalPages());
        assertEquals(77, new PageResult<>(List.of(), 7632, 0, 100).totalPages());
    }

    @Test
    void firstAndLastRowDescribeThePage() {
        PageResult<Integer> second = new PageResult<>(List.of(), 7632, 1, 100);
        assertEquals(101, second.firstRow());
        assertEquals(200, second.lastRow());
        PageResult<Integer> last = new PageResult<>(List.of(), 7632, 76, 100);
        assertEquals(7601, last.firstRow());
        assertEquals(7632, last.lastRow(), "the last page ends at the real total, not at a full 100");
        assertEquals(0, new PageResult<>(List.of(), 0, 0, 100).firstRow());
        assertEquals(0, new PageResult<>(List.of(), 0, 0, 100).lastRow());
    }

    @Test
    void sliceReturnsTheRightRows() {
        List<Integer> all = numbers(250);
        PageResult<Integer> p0 = PageResult.slice(all, 0, 100);
        PageResult<Integer> p2 = PageResult.slice(all, 2, 100);
        assertEquals(100, p0.items().size());
        assertEquals(1, p0.items().get(0));
        assertEquals(50, p2.items().size(), "the last page holds the remainder");
        assertEquals(201, p2.items().get(0));
        assertEquals(250, p2.total());
    }

    @Test
    void sliceBeyondTheEndFallsBackToTheLastPage() {
        PageResult<Integer> r = PageResult.slice(numbers(250), 99, 100);
        assertEquals(2, r.page());
        assertEquals(50, r.items().size());
        assertTrue(PageResult.slice(numbers(0), 5, 100).items().isEmpty());
    }

    @Test
    void sliceCoversEveryRowExactlyOnce() {
        List<Integer> all = numbers(1234);
        List<Integer> back = new ArrayList<>();
        int pages = new PageResult<>(List.of(), all.size(), 0, 100).totalPages();
        for (int p = 0; p < pages; p++) back.addAll(PageResult.slice(all, p, 100).items());
        assertEquals(all, back, "walking every page returns the whole list in order, nothing lost or repeated");
    }

    @Test
    void clampPageKeepsAStaleOrWrongPageNumberInRange() {
        assertEquals(0, PageResult.clampPage(-3, 7632, 100));
        assertEquals(76, PageResult.clampPage(9999, 7632, 100));
        assertEquals(5, PageResult.clampPage(5, 7632, 100));
        assertEquals(0, PageResult.clampPage(4, 0, 100), "no rows: always page 0");
        assertEquals(0, PageResult.clampPage(1, 100, 100), "exactly one full page: page 1 does not exist");
    }

    @Test
    void parsePageToleratesGarbage() {
        assertEquals(3, PageResult.parsePage("3"));
        assertEquals(3, PageResult.parsePage(" 3 "));
        assertEquals(0, PageResult.parsePage("abc"));
        assertEquals(0, PageResult.parsePage(""));
        assertEquals(0, PageResult.parsePage(null));
        assertEquals(0, PageResult.parsePage("-7"));
        assertEquals(0, PageResult.parsePage("99999999999999999999"), "an absurdly large number must not throw");
    }
}
