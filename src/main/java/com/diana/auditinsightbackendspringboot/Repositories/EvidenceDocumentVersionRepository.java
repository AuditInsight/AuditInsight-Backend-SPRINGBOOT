package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EvidenceDocumentVersionRepository extends ReactiveCrudRepository<EvidenceDocumentVersion, UUID> {
    Mono<EvidenceDocumentVersion> findFirstByEvidenceIdOrderByVersionNoDesc(UUID evidenceId);

    Flux<EvidenceDocumentVersion> findAllByEvidenceIdOrderByVersionNoAsc(UUID evidenceId);
}
