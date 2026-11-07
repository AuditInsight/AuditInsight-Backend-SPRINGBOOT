package com.diana.auditinsightbackendspringboot.DTOs;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One audit-trail entry as shown to organisation members. */
public record AuditEventResponse(UUID id, String eventType, String actorName, Integer versionNo, String sha256,
                                 String ipAddress, String userAgent, String details, OffsetDateTime createdAt) {}
