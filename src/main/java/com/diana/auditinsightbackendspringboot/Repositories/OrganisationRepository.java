package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.Organisation;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrganisationRepository extends ReactiveCrudRepository<Organisation, UUID> {
    Flux<Organisation> findAllByClientId(UUID clientId);

    /** Admin org list — {@code search} (nullable) matches against the org name, case-insensitively. */
    @Query("SELECT * FROM organisation WHERE (:search IS NULL OR LOWER(name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Organisation> findPage(String search, int limit, long offset);

    @Query("SELECT COUNT(*) FROM organisation WHERE (:search IS NULL OR LOWER(name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Mono<Long> countPage(String search);
}
