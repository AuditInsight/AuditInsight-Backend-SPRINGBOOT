package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.OrganisationMember;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrganisationMemberRepository extends ReactiveCrudRepository<OrganisationMember, UUID> {
    Flux<OrganisationMember> findAllByOrganisationId(UUID organisationId);
    Flux<OrganisationMember> findAllByUserId(Long userId);
    Mono<OrganisationMember> findByOrganisationIdAndUserId(UUID organisationId, Long userId);
    Mono<Void> deleteByOrganisationIdAndUserId(UUID organisationId, Long userId);
}
