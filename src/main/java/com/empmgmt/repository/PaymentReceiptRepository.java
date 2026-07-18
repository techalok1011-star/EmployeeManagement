package com.empmgmt.repository;

import com.empmgmt.entity.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    Optional<PaymentReceipt> findByPaymentEntryId(Long paymentEntryId);

    boolean existsByPaymentEntryId(Long paymentEntryId);

    /** Batched existence check - avoids N+1 when flagging a whole list of entries. */
    @Query("SELECT r.paymentEntry.id FROM PaymentReceipt r WHERE r.paymentEntry.id IN :entryIds")
    Set<Long> findPaymentEntryIdsWithReceipt(@Param("entryIds") Collection<Long> entryIds);
}
