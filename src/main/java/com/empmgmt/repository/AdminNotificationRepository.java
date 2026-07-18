package com.empmgmt.repository;

import com.empmgmt.entity.AdminNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    List<AdminNotification> findAllByOrderByCreatedAtDesc();

    long countByReadFalse();

    @Modifying
    @Query("UPDATE AdminNotification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.read = false")
    int markAllRead();
}
