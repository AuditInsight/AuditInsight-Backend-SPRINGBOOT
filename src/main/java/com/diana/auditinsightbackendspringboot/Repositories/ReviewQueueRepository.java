package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.ReviewStatus;
import com.diana.auditinsightbackendspringboot.Models.ReviewQueue;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReviewQueueRepository extends ReactiveCrudRepository<ReviewQueue, UUID> {
    Flux<ReviewQueue> findAllByOrganisationId(UUID organisationId);
    Flux<ReviewQueue> findByTransactionIdAndStatus(String transactionId, ReviewStatus status);
    Mono<Boolean> existsByTransactionIdAndFlaggedByAndStatus(
            String transactionId, String flaggedBy, ReviewStatus status);
}
