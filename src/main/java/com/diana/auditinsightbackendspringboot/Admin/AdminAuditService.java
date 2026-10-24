package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Repositories.AdminAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Records platform-admin mutations (user activate/deactivate, subscription tier changes,
 * plan price edits) to {@code admin_audit_log}. Callers chain {@link #record} into the same
 * reactive pipeline as the mutation it's auditing, rather than firing it off separately, so a
 * failed write surfaces instead of silently dropping the audit trail.
 */
@Slf4j
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    // Built locally rather than injected: this app's Spring-managed ObjectMapper bean is the
    // Jackson 3 (tools.jackson) one autoconfigured for Spring Boot 4, not this com.fasterxml type —
    // jackson-databind 2.x is only on the classpath transitively via jjwt-jackson, with no bean for it.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminAuditService(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    public Mono<AdminAuditLog> record(Long adminUserId, String action, String targetType, String targetId,
                                       Object before, Object after) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminUserId(adminUserId);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setBeforeState(toJson(before));
        entry.setAfterState(toJson(after));
        entry.setCreatedAt(LocalDateTime.now());
        return adminAuditLogRepository.save(entry);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize admin audit log state for {}", value.getClass().getSimpleName(), e);
            return String.valueOf(value);
        }
    }
}
