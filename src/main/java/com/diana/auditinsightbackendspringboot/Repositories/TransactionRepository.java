package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.EvidenceStatus;
import com.diana.auditinsightbackendspringboot.Enum.TransactionStatus;
import com.diana.auditinsightbackendspringboot.Models.Transaction;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TransactionRepository extends ReactiveCrudRepository<Transaction, String> {
    Flux<Transaction> findAllByOrganisationId(UUID organisationId);
    Mono<Long> countByOrganisationId(UUID organisationId);
    Flux<Transaction> findByStatusAndEvidenceStatusAndCreatedAtBefore(
            TransactionStatus status, EvidenceStatus evidenceStatus, LocalDateTime before);
}
