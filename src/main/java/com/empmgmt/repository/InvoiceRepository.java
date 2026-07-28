package com.empmgmt.repository;

import com.empmgmt.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByOrderByInvoiceDateDescCreatedAtDesc();

    List<Invoice> findByPartyNameOrderByInvoiceDateDescCreatedAtDesc(String partyName);

    List<Invoice> findByInvoiceDateBetween(LocalDate start, LocalDate end);

    List<Invoice> findByCreatedByOrderByInvoiceDateDescCreatedAtDesc(String createdBy);

    List<Invoice> findByCreatedByAndInvoiceDateOrderByCreatedAtDesc(String createdBy, LocalDate invoiceDate);

    List<Invoice> findByCreatedByAndInvoiceDateBetweenOrderByInvoiceDateDescCreatedAtDesc(
            String createdBy, LocalDate start, LocalDate end);

    List<Invoice> findByInvoiceNumberStartingWith(String prefix);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i")
    BigDecimal sumAllAmounts();

    /** Returns [partyName, sumAmount] pairs for all parties that have invoices */
    @Query("SELECT i.partyName, SUM(i.amount) FROM Invoice i GROUP BY i.partyName")
    List<Object[]> sumAmountGroupedByPartyName();

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.createdBy = :createdBy AND i.invoiceDate = :date")
    BigDecimal sumAmountByCreatedByAndDate(@Param("createdBy") String createdBy, @Param("date") LocalDate date);

    long countByCreatedByAndInvoiceDate(String createdBy, LocalDate invoiceDate);

    /** Returns [createdBy, sumAmount, count] triples for all invoices with a non-null creator. */
    @Query("SELECT i.createdBy, SUM(i.amount), COUNT(i) FROM Invoice i WHERE i.createdBy IS NOT NULL GROUP BY i.createdBy")
    List<Object[]> sumAndCountGroupedByCreatedBy();

    /** Same, restricted to a date range - used for the "this month" column. */
    @Query("SELECT i.createdBy, SUM(i.amount), COUNT(i) FROM Invoice i WHERE i.createdBy IS NOT NULL AND i.invoiceDate BETWEEN :start AND :end GROUP BY i.createdBy")
    List<Object[]> sumAndCountGroupedByCreatedByInRange(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
