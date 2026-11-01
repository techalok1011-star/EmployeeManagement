package com.empmgmt.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StaticCacheFilterTest {

    @Test
    void fingerprintedFilesAreKeptForAYear() {
        assertEquals(StaticCacheFilter.FOREVER, StaticCacheFilter.policyFor("/js/theme-0123456789abcdef0123456789abcdef.js"));
        assertEquals(StaticCacheFilter.FOREVER, StaticCacheFilter.policyFor("/css/theme-fedcba9876543210fedcba9876543210.css"));
        assertEquals(StaticCacheFilter.FOREVER, StaticCacheFilter.policyFor("/js/map-app/assets/index-C-wejuyT.js"));
        assertEquals(StaticCacheFilter.FOREVER, StaticCacheFilter.policyFor("/js/map-app/assets/index-BCkvm_HX.css"));
    }

    @Test
    void unversionedScriptsMustRevalidateSoTheyCanNeverGoStale() {
        assertEquals(StaticCacheFilter.REVALIDATE, StaticCacheFilter.policyFor("/js/theme.js"));
        assertEquals(StaticCacheFilter.REVALIDATE, StaticCacheFilter.policyFor("/css/theme.css"));
        // a hash-looking name that is not 32 hex chars is NOT treated as fingerprinted
        assertEquals(StaticCacheFilter.REVALIDATE, StaticCacheFilter.policyFor("/js/pt-loading-abc123.js"));
        assertEquals(StaticCacheFilter.REVALIDATE, StaticCacheFilter.policyFor("/sw.js"));
        assertEquals(StaticCacheFilter.REVALIDATE, StaticCacheFilter.policyFor("/manager-manifest.json"));
    }

    @Test
    void iconsGetOneDay() {
        assertEquals(StaticCacheFilter.ONE_DAY, StaticCacheFilter.policyFor("/icons/icon-192.png"));
    }

    @Test
    void pagesAndDataAreLeftAloneSoTheyStayNoStore() {
        assertNull(StaticCacheFilter.policyFor("/admin/dashboard"));
        assertNull(StaticCacheFilter.policyFor("/admin/collection-map/data"));
        assertNull(StaticCacheFilter.policyFor("/login"));
        assertNull(StaticCacheFilter.policyFor("/health"));
        assertNull(StaticCacheFilter.policyFor("/manager/entries/12/edit"));
    }
}
