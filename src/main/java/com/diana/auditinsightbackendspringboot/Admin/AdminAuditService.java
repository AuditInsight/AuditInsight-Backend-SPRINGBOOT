package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Repositories.AdminAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records platform-admin mutations (user activate/deactivate, subscription changes, plan price edits, trials,
 * campaigns, emergency access) to {@code admin_audit_log}. Callers chain {@link #record} into the same reactive
 * pipeline as the mutation it's auditing, so a failed write surfaces instead of silently dropping the audit trail.
 * <p>
 * Before/after states are stored as JSON with ISO-8601 dates. If a state can't be serialised, a small JSON object
 * saying so is stored instead of an unreadable {@code toString()}.
 */
@Slf4j
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    // Jackson 3 (already on the classpath via Spring Boot 4) handles java.time natively and writes ISO-8601 strings.
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AdminAuditService(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    public Mono<AdminAuditLog> record(Long adminUserId, String action, String targetType, String targetId,
                                       Object before, Object after) {
        return record(adminUserId, action, targetType, targetId, before, after, null);
    }

    /** As above, with the administrator's stated reason stored in its own column. */
    public Mono<AdminAuditLog> record(Long adminUserId, String action, String targetType, String targetId,
                                       Object before, Object after, String reason) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminUserId(adminUserId);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setBeforeState(toJson(before));
        entry.setAfterState(toJson(after));
        entry.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        entry.setCreatedAt(LocalDateTime.now());
        return adminAuditLogRepository.save(entry);
    }

    String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Failed to serialise admin audit state for {}", value.getClass().getSimpleName(), e);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("serializationError", true);
            fallback.put("type", value.getClass().getSimpleName());
            return jsonMapper.writeValueAsString(fallback);
        }
    }
}
