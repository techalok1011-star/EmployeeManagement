package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Singleton settings row (id is always {@link #SETTINGS_ID}) controlling the
 * daily WhatsApp reminder scheduler.
 */
@Entity
@Table(name = "notification_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettings {

    public static final Long SETTINGS_ID = 1L;

    @Id
    private Long id;

    @Column(name = "daily_reminder_enabled", nullable = false)
    private boolean dailyReminderEnabled;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
