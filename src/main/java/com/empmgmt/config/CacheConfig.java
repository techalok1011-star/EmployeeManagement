package com.empmgmt.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * In-memory caching for the expensive, read-heavy financial summary methods
 * (party outstanding, full ledger, aging, payment behavior). Single-instance
 * app on the free tier, so a local Caffeine cache is enough - no need for a
 * distributed cache like Redis.
 * <p>
 * 5-minute TTL is a safety net; the real invalidation path is the
 * {@code @CacheEvict} annotations on every invoice/payment write, which clear
 * these caches immediately so figures are never stale after an edit.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PARTY_OUTSTANDING = "partyOutstanding";
    public static final String ALL_PARTY_LEDGERS = "allPartyLedgers";
    public static final String PARTY_LEDGER = "partyLedger";
    public static final String AGING_REPORT = "agingReport";
    public static final String PAYMENT_BEHAVIOR = "paymentBehavior";
    public static final String INVOICE_STATS = "invoiceStats";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                PARTY_OUTSTANDING, ALL_PARTY_LEDGERS, PARTY_LEDGER,
                AGING_REPORT, PAYMENT_BEHAVIOR, INVOICE_STATS);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500));
        return manager;
    }
}
