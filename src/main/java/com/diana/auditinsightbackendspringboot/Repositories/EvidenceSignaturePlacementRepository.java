package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.EvidenceSignaturePlacement;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface EvidenceSignaturePlacementRepository extends ReactiveCrudRepository<EvidenceSignaturePlacement, UUID> {
    Flux<EvidenceSignaturePlacement> findAllBySignatureId(UUID signatureId);
}
