package com.diana.auditinsightbackendspringboot.DTOs;

import java.time.LocalDateTime;

/** State of the platform-wide emergency override. */
public record AdminFreeAccessResponse(
        boolean enabled,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime updatedAt,
        String reason
) {}
