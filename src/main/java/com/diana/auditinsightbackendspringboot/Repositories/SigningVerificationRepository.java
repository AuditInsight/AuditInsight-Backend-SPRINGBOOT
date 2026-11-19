package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.SigningVerification;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface SigningVerificationRepository extends ReactiveCrudRepository<SigningVerification, UUID> {

    /** The newest code sent to this user for this document; only that one can be used. */
    Mono<SigningVerification> findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(Long userId, UUID evidenceId);

    /** Codes sent to the user since the given time, across all documents (limits email volume). */
    Mono<Long> countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    @Modifying
    @Query("UPDATE signing_verifications SET attempts = attempts + 1 WHERE id = :id")
    Mono<Integer> incrementAttempts(UUID id);

    /** Marks the code used. Returns 0 when another request already used it, so a code can only sign once. */
    @Modifying
    @Query("UPDATE signing_verifications SET consumed_at = :now WHERE id = :id AND consumed_at IS NULL")
    Mono<Integer> consumeIfUnused(UUID id, LocalDateTime now);

    /** Gives the code back when the signature it was claimed for could not be saved. */
    @Modifying
    @Query("UPDATE signing_verifications SET consumed_at = NULL WHERE id = :id")
    Mono<Integer> release(UUID id);
}
