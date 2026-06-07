package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.Organisation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface OrganisationRepository extends ReactiveCrudRepository<Organisation, UUID> {
    Flux<Organisation> findAllByClientId(UUID clientId);
}
