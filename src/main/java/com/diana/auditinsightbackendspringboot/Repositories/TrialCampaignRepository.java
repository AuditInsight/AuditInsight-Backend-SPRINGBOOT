package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.TrialCampaign;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrialCampaignRepository extends ReactiveCrudRepository<TrialCampaign, UUID> {
    /** The enabled campaign, if any (at most one, enforced by a unique index). */
    Mono<TrialCampaign> findFirstByEnabledTrue();

    Flux<TrialCampaign> findAllByOrderByCreatedAtDesc();
}
