package com.empmgmt.repository;

import com.empmgmt.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findTop30ByOrderBySentAtDesc();

    List<NotificationLog> findByPartyNameOrderBySentAtDesc(String partyName);

    List<NotificationLog> findBySentAtAfterOrderBySentAtDesc(@Param("since") LocalDateTime since);

    /** Returns [status, count] pairs across the whole table. */
    @Query("SELECT n.status, COUNT(n) FROM NotificationLog n GROUP BY n.status")
    List<Object[]> countGroupedByStatus();
}
