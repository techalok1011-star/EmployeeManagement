package com.empmgmt.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Central source of "now"/"today" pinned to Asia/Kolkata. Plain {@code LocalDateTime.now()}/
 * {@code LocalDate.now()} use the JVM's system-default zone, which is UTC on Render's container
 * (confirmed root cause of the NotificationScheduler cron and LoginSessionService timestamp bugs)
 * - every timestamp/date captured that way lands 5.5h behind actual IST, or a full calendar day
 * off between 00:00-05:29 IST. All wall-clock reads in this app should go through here instead.
 */
public final class IstClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private IstClock() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
