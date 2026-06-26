package com.empmgmt.repository;

import com.empmgmt.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByOrderByInvoiceDateDescCreatedAtDesc();

    List<Invoice> findByPartyNameOrderByInvoiceDateDescCreatedAtDesc(String partyName);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i")
    BigDecimal sumAllAmounts();

    /** Returns [partyName, sumAmount] pairs for all parties that have invoices */
    @Query("SELECT i.partyName, SUM(i.amount) FROM Invoice i GROUP BY i.partyName")
    List<Object[]> sumAmountGroupedByPartyName();
}
