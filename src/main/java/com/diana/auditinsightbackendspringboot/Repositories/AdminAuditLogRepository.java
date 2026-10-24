package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AdminAuditLogRepository extends ReactiveCrudRepository<AdminAuditLog, UUID> {

    @Query("SELECT * FROM admin_audit_log ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<AdminAuditLog> findPage(int limit, long offset);

    @Query("SELECT COUNT(*) FROM admin_audit_log")
    Mono<Long> countAll();
}
