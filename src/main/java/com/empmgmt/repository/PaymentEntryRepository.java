package com.empmgmt.repository;

import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public interface PaymentEntryRepository extends JpaRepository<PaymentEntry, Long> {

    List<PaymentEntry> findByEmployeeOrderByCreatedAtDesc(User employee);

    List<PaymentEntry> findByEmployeeAndEntryDateOrderByCreatedAtDesc(User employee, LocalDate date);

    List<PaymentEntry> findAllByOrderByCreatedAtDesc();

    List<PaymentEntry> findByPartyNameOrderByEntryDateDescCreatedAtDesc(String partyName);

    List<PaymentEntry> findByEntryDateOrderByCreatedAtDesc(LocalDate date);

    @Query("SELECT SUM(p.amount) FROM PaymentEntry p WHERE p.employee = :employee AND p.entryDate = :date")
    BigDecimal sumAmountByEmployeeAndDate(@Param("employee") User employee, @Param("date") LocalDate date);

    @Query("SELECT COUNT(p) FROM PaymentEntry p WHERE p.employee = :employee AND p.entryDate = :date")
    long countByEmployeeAndDate(@Param("employee") User employee, @Param("date") LocalDate date);

    @Query("SELECT SUM(p.amount) FROM PaymentEntry p WHERE p.entryDate = :date")
    BigDecimal sumAmountByDate(@Param("date") LocalDate date);

    @Query("SELECT p FROM PaymentEntry p WHERE p.employee.id = :employeeId ORDER BY p.createdAt DESC")
    List<PaymentEntry> findByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT p FROM PaymentEntry p WHERE p.employee.id = :employeeId AND p.entryDate BETWEEN :start AND :end ORDER BY p.entryDate DESC, p.createdAt DESC")
    List<PaymentEntry> findByEmployeeIdAndDateRange(@Param("employeeId") Long employeeId,
                                                     @Param("start") LocalDate start,
                                                     @Param("end") LocalDate end);

    @Query("SELECT p FROM PaymentEntry p WHERE p.entryDate BETWEEN :start AND :end ORDER BY p.entryDate DESC, p.createdAt DESC")
    List<PaymentEntry> findAllByDateRange(@Param("start") LocalDate start,
                                          @Param("end") LocalDate end);

    /** Returns [partyName, sumAmount] pairs for all parties that have payment entries */
    @Query("SELECT p.partyName, SUM(p.amount) FROM PaymentEntry p GROUP BY p.partyName")
    List<Object[]> sumAmountGroupedByPartyName();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentEntry p")
    BigDecimal sumAllAmounts();

    /** Returns [employeeId, sumAmount, count] triples, all-time, one row per employee with entries. */
    @Query("SELECT p.employee.id, SUM(p.amount), COUNT(p) FROM PaymentEntry p GROUP BY p.employee.id")
    List<Object[]> sumAndCountGroupedByEmployee();

    /** Returns [employeeId, sumAmount, count] triples for entries within the given date range. */
    @Query("SELECT p.employee.id, SUM(p.amount), COUNT(p) FROM PaymentEntry p WHERE p.entryDate BETWEEN :start AND :end GROUP BY p.employee.id")
    List<Object[]> sumAndCountGroupedByEmployeeInRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // ── Dashboard aggregates ──────────────────────────────────────────────────────────────────
    // These exist so the dashboard doesn't have to pull every PaymentEntry into memory just to
    // total it up. Only days present in the range come back; the caller zero-fills the gaps.

    /** Returns [entryDate, sumAmount] pairs for entries within the range, one row per day. */
    @Query("SELECT p.entryDate, SUM(p.amount) FROM PaymentEntry p WHERE p.entryDate BETWEEN :start AND :end GROUP BY p.entryDate")
    List<Object[]> sumAmountGroupedByEntryDate(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Returns [modeOfPayment, sumAmount] pairs, all-time, one row per mode actually used. */
    @Query("SELECT p.modeOfPayment, SUM(p.amount) FROM PaymentEntry p GROUP BY p.modeOfPayment")
    List<Object[]> sumAmountGroupedByMode();

    /** Returns [partyName, sumAmount] pairs, all-time, biggest first. Use a Pageable to cap it. */
    @Query("SELECT p.partyName, SUM(p.amount) FROM PaymentEntry p GROUP BY p.partyName ORDER BY SUM(p.amount) DESC")
    List<Object[]> sumAmountGroupedByPartyNameOrdered(Pageable pageable);

    long countByEntryDate(LocalDate date);

    /**
     * Most recent entries by createdAt, newest first, with nulls last.
     * <p>
     * NULLS LAST is deliberate and load-bearing: a large share of rows (the legacy Tally imports)
     * have a null createdAt, and Postgres sorts NULLs FIRST on a DESC ordering. Without it this
     * would return nothing but null-dated rows. It matches the Comparator.nullsLast ordering the
     * in-memory version used. JOIN FETCH avoids an N+1 on the LAZY employee that mapToResponse reads.
     */
    @Query("SELECT p FROM PaymentEntry p JOIN FETCH p.employee ORDER BY p.createdAt DESC NULLS LAST")
    List<PaymentEntry> findRecentWithEmployee(Pageable pageable);

    // ── Party merge ────────────────────────────────────────────────────────

    long countByPartyName(String partyName);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentEntry p WHERE p.partyName = :partyName")
    BigDecimal sumAmountByPartyName(@Param("partyName") String partyName);

    /** Bulk re-point every payment filed under one party_name text onto another. See PartyMergeService. */
    @Modifying
    @Query("UPDATE PaymentEntry p SET p.partyName = :toName WHERE p.partyName = :fromName")
    int repointPartyName(@Param("fromName") String fromName, @Param("toName") String toName);

    // ── Voucher audit ──────────────────────────────────────────────────────

    /** Raw [id, receiptVchNo, entryDate, amount, partyName] rows for one financial year, for VoucherAuditService to parse. */
    @Query("SELECT p.id, p.receiptVchNo, p.entryDate, p.amount, p.partyName FROM PaymentEntry p " +
           "WHERE p.entryDate >= :fyStart AND p.receiptVchNo IS NOT NULL")
    List<Object[]> findVoucherRowsFrom(@Param("fyStart") LocalDate fyStart);
}
