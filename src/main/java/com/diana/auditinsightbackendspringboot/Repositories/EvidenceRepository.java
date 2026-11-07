package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.Evidence;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EvidenceRepository extends ReactiveCrudRepository<Evidence, UUID> {
    Flux<Evidence> findAllByOrganisationId(UUID organisationId);
    Flux<Evidence> findAllByTransactionId(UUID transactionId);
    Mono<Long> countByTransactionId(UUID transactionId);

    /**
     * Records a Word/Excel document's PDF conversion only if none is recorded yet (returns 1), so two requests
     * converting at the same time can't leave signers working on different PDFs: the loser re-reads the winner's.
     */
    @Modifying
    @Query("UPDATE evidence SET pdf_rendition_url = :url, pdf_rendition_sha256 = :sha256 " +
            "WHERE id = :id AND pdf_rendition_url IS NULL")
    Mono<Integer> setPdfRenditionIfAbsent(UUID id, String url, String sha256);
}
