package com.empmgmt.repository;

import com.empmgmt.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {
    boolean existsByCombined(String combined);
    Optional<Party> findByCombined(String combined);
    List<Party> findTop50ByCombinedContainingIgnoreCase(String combined);
    List<Party> findTop50ByNameContainingIgnoreCaseOrTrailingNumberContainingIgnoreCase(String name, String trailingNumber);
    List<Party> findByCombinedIn(Collection<String> combined);
    List<Party> findByWhatsappOptInTrueAndPhoneNotNull();
    long countByPhoneIsNotNull();
    long countByWhatsappOptInTrueAndPhoneIsNotNull();

    // ── Party merge ────────────────────────────────────────────────────────

    /**
     * Every party_name string used in invoices/payment_entries with no matching parties.combined row
     * - the free-text-no-FK split that keeps corrupting outstanding/churn views (PRASANG/SURAJ/USMANI
     * pattern, see project memory). One row per orphaned name with its invoice/payment counts+sums,
     * biggest-money-first. Native SQL: spans two entities, no clean JPQL equivalent.
     */
    @Query(value = """
            WITH ledger AS (
                SELECT party_name, amount, 'inv' AS src FROM invoices
                UNION ALL
                SELECT party_name, amount, 'pay' AS src FROM payment_entries
            )
            SELECT l.party_name,
                   COUNT(*) FILTER (WHERE l.src = 'inv')                              AS invoice_count,
                   COALESCE(SUM(l.amount) FILTER (WHERE l.src = 'inv'), 0)            AS invoice_amount,
                   COUNT(*) FILTER (WHERE l.src = 'pay')                              AS payment_count,
                   COALESCE(SUM(l.amount) FILTER (WHERE l.src = 'pay'), 0)            AS payment_amount
            FROM ledger l
            WHERE NOT EXISTS (SELECT 1 FROM parties p WHERE p.combined = l.party_name)
            GROUP BY l.party_name
            ORDER BY (COALESCE(SUM(l.amount) FILTER (WHERE l.src = 'inv'), 0)
                    + COALESCE(SUM(l.amount) FILTER (WHERE l.src = 'pay'), 0)) DESC
            """, nativeQuery = true)
    List<Object[]> findOrphanedPartyNames();

    /**
     * Distinct party_name text actually used in invoices/payment_entries matching q - covers orphaned
     * names (no parties row) as well as linked ones, unlike {@link #findTop50ByCombinedContainingIgnoreCase}
     * which only searches the parties table. Backs the merge tool's "From" autocomplete.
     */
    @Query(value = """
            SELECT DISTINCT party_name FROM (
                SELECT party_name FROM invoices WHERE party_name ILIKE CONCAT('%', :q, '%')
                UNION
                SELECT party_name FROM payment_entries WHERE party_name ILIKE CONCAT('%', :q, '%')
            ) x
            ORDER BY party_name
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findLedgerPartyNamesMatching(@Param("q") String q, @Param("limit") int limit);
}

