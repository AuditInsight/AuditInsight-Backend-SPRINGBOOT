package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SubscriptionRepository extends ReactiveCrudRepository<Subscription, UUID> {
    Mono<Subscription> findByOrganisationIdAndStatus(UUID organisationId, SubscriptionStatus status);
}
