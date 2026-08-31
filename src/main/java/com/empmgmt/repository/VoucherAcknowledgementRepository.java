package com.empmgmt.repository;

import com.empmgmt.entity.VoucherAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherAcknowledgementRepository extends JpaRepository<VoucherAcknowledgement, Long> {

    List<VoucherAcknowledgement> findBySeriesAndFinancialYearOrderByRangeStart(String series, String financialYear);
}
