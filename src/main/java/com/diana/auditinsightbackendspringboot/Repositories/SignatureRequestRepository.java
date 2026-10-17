package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.SignatureRequestStatus;
import com.diana.auditinsightbackendspringboot.Models.SignatureRequest;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SignatureRequestRepository extends ReactiveCrudRepository<SignatureRequest, UUID> {
    Flux<SignatureRequest> findAllByEvidenceIdAndStatus(UUID evidenceId, SignatureRequestStatus status);
    Flux<SignatureRequest> findAllByRequestedUserIdAndStatusOrderByCreatedAtDesc(Long requestedUserId, SignatureRequestStatus status);
    Mono<SignatureRequest> findByEvidenceIdAndRequestedUserIdAndStatus(UUID evidenceId, Long requestedUserId, SignatureRequestStatus status);
}
