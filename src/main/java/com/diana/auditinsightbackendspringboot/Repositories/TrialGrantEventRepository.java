package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.TrialGrantEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Append-only: the application only inserts and reads trial history. */
public interface TrialGrantEventRepository extends ReactiveCrudRepository<TrialGrantEvent, UUID> {
    Flux<TrialGrantEvent> findAllByOrganisationIdOrderByCreatedAtAsc(UUID organisationId);
}
