package com.empmgmt.repository;

import com.empmgmt.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {

    List<TransactionLog> findByEmployeeUsernameOrderByPerformedAtDesc(String username);

    List<TransactionLog> findAllByOrderByPerformedAtDesc();

    List<TransactionLog> findByEntryIdOrderByPerformedAtDesc(Long entryId);
}
