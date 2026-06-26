package com.empmgmt.repository;

import com.empmgmt.entity.PaymentEntry;
import com.empmgmt.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
