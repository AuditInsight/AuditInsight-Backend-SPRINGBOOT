package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialSource;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TrialGrantRepository extends ReactiveCrudRepository<TrialGrant, UUID> {
    /** The organisation's open grant (at most one, enforced by a unique index); it may already have expired. */
    Mono<TrialGrant> findFirstByOrganisationIdAndStatus(UUID organisationId, TrialGrantStatus status);

    Flux<TrialGrant> findAllByOrganisationIdOrderByCreatedAtDesc(UUID organisationId);

    Mono<Boolean> existsByOwnerClientIdAndSource(UUID ownerClientId, TrialSource source);

    Mono<Long> countByCampaignId(UUID campaignId);

    /** Open grants whose end falls in a window (for reminder emails). */
    Flux<TrialGrant> findAllByStatusAndEndsAtBetween(TrialGrantStatus status, LocalDateTime from, LocalDateTime to);
}
