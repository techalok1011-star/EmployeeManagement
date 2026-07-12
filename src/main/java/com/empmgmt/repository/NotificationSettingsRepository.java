package com.empmgmt.repository;

import com.empmgmt.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {
}
