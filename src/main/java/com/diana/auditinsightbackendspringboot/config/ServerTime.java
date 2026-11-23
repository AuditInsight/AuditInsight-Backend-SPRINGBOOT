package com.diana.auditinsightbackendspringboot.config;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Payment and subscription times are stored as TIMESTAMP (no time zone) on the server's clock, which is UTC in
 * production. Sent to a browser as-is (e.g. "2026-11-20T16:03:00") they are read as the viewer's local time and show
 * two hours early in Rwanda. Converting to an offset time ("2026-11-20T16:03:00Z") lets the browser show the right
 * local time.
 */
public final class ServerTime {

    private ServerTime() {}

    public static OffsetDateTime withOffset(LocalDateTime serverLocal) {
        return withOffset(serverLocal, ZoneId.systemDefault());
    }

    static OffsetDateTime withOffset(LocalDateTime serverLocal, ZoneId serverZone) {
        return serverLocal == null ? null : serverLocal.atZone(serverZone).toOffsetDateTime();
    }
}
