package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EvidenceDocumentVersionRepository extends ReactiveCrudRepository<EvidenceDocumentVersion, UUID> {
    /** Latest version of any kind — used for numbering the next version. */
    Mono<EvidenceDocumentVersion> findFirstByEvidenceIdOrderByVersionNoDesc(UUID evidenceId);

    /** Latest version of one kind (DOCUMENT or CERTIFICATE) — what the document is currently served and built from. */
    Mono<EvidenceDocumentVersion> findFirstByEvidenceIdAndRenditionOrderByVersionNoDesc(UUID evidenceId, String rendition);

    Flux<EvidenceDocumentVersion> findAllByEvidenceIdOrderByVersionNoAsc(UUID evidenceId);
}
