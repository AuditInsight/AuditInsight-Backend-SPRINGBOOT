package com.diana.auditinsightbackendspringboot.DTOs;

import java.time.LocalDateTime;

public record AdminFreeAccessResponse(
        boolean enabled,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime updatedAt
) {}
