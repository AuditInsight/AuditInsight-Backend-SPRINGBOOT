package com.diana.auditinsightbackendspringboot.DTOs;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An organisation whose only access right now is the emergency override: it loses access when the override ends,
 * unless its queued paid time (if any) has started by then.
 */
public record EmergencyOverrideDependent(UUID organisationId, String name, LocalDateTime nextPaidStart) {}
