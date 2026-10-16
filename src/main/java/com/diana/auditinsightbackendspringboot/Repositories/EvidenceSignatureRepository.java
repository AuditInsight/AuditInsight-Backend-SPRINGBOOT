package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.EvidenceSignature;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EvidenceSignatureRepository extends ReactiveCrudRepository<EvidenceSignature, UUID> {
    Flux<EvidenceSignature> findAllByEvidenceIdOrderBySignedAtAsc(UUID evidenceId);
    Mono<EvidenceSignature> findByEvidenceIdAndSignerId(UUID evidenceId, Long signerId);
}
