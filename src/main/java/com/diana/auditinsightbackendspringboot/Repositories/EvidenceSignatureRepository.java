package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.EvidenceSignature;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EvidenceSignatureRepository extends ReactiveCrudRepository<EvidenceSignature, UUID> {
    Flux<EvidenceSignature> findAllByEvidenceIdOrderBySignedAtAsc(UUID evidenceId);
    Mono<EvidenceSignature> findByEvidenceIdAndSignerId(UUID evidenceId, Long signerId);

    Mono<Long> countByEvidenceId(UUID evidenceId);

    /** One row per signature in the organisation, so callers can count signatures per document in one query. */
    @Query("SELECT evidence_id FROM evidence_signatures WHERE organisation_id = :organisationId")
    Flux<UUID> findSignedEvidenceIdsByOrganisationId(UUID organisationId);

    /** One row per signature on the transaction's documents, so callers can count signatures per document in one query. */
    @Query("SELECT s.evidence_id FROM evidence_signatures s JOIN evidence e ON e.id = s.evidence_id " +
            "WHERE e.transaction_id = :transactionId")
    Flux<UUID> findSignedEvidenceIdsByTransactionId(UUID transactionId);
}
