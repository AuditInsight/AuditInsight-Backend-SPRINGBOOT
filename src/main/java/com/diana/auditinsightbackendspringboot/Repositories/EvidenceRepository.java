package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.Evidence;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EvidenceRepository extends ReactiveCrudRepository<Evidence, UUID> {
    Flux<Evidence> findAllByOrganisationId(UUID organisationId);
    Flux<Evidence> findAllByTransactionId(String transactionId);
    Mono<Long> countByTransactionId(String transactionId);
}
