package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.EvidenceAuditEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Append-only: the application only ever inserts and reads audit events. */
public interface EvidenceAuditEventRepository extends ReactiveCrudRepository<EvidenceAuditEvent, UUID> {
    Flux<EvidenceAuditEvent> findAllByEvidenceIdOrderByCreatedAtAsc(UUID evidenceId);
}
