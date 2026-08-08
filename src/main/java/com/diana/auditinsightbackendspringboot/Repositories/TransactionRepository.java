package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.EvidenceStatus;
import com.diana.auditinsightbackendspringboot.Enum.TransactionStatus;
import com.diana.auditinsightbackendspringboot.Models.Transaction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TransactionRepository extends ReactiveCrudRepository<Transaction, String> {
    Flux<Transaction> findAllByOrganisationId(UUID organisationId);
    Flux<Transaction> findByStatusAndEvidenceStatusAndCreatedAtBefore(
            TransactionStatus status, EvidenceStatus evidenceStatus, LocalDateTime before);

    /**
     * Atomically issues the next per-organisation transaction sequence number via an upsert +
     * RETURNING, so concurrent creates can't both compute the same "next" number the way a
     * plain COUNT(*)-then-format approach can.
     */
    @Query("INSERT INTO transaction_counters (organisation_id, next_seq) VALUES (:organisationId, 1) " +
            "ON CONFLICT (organisation_id) DO UPDATE SET next_seq = transaction_counters.next_seq + 1 " +
            "RETURNING next_seq")
    Mono<Integer> nextTransactionSequence(UUID organisationId);
}
