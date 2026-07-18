package com.empmgmt.repository;

import com.empmgmt.entity.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    Optional<PaymentReceipt> findByPaymentEntryId(Long paymentEntryId);

    boolean existsByPaymentEntryId(Long paymentEntryId);

    /** Projection for batched flagging - avoids N+1 when annotating a whole list of entries. */
    interface ReceiptFlag {
        Long getPaymentEntryId();
        BigDecimal getLatitude();
        BigDecimal getLongitude();
    }

    @Query("SELECT r.paymentEntry.id AS paymentEntryId, r.latitude AS latitude, r.longitude AS longitude " +
           "FROM PaymentReceipt r WHERE r.paymentEntry.id IN :entryIds")
    List<ReceiptFlag> findReceiptFlags(@Param("entryIds") Collection<Long> entryIds);
}
