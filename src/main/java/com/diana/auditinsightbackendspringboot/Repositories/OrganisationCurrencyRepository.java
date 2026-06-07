package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.OrganisationCurrency;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrganisationCurrencyRepository extends ReactiveCrudRepository<OrganisationCurrency, UUID> {
    Flux<OrganisationCurrency> findAllByOrganisationId(UUID organisationId);
    Mono<Void> deleteAllByOrganisationId(UUID organisationId);
}
