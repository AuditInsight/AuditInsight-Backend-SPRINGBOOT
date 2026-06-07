package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.InvitationStatus;
import com.diana.auditinsightbackendspringboot.Models.OrganisationInvitation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrganisationInvitationRepository extends ReactiveCrudRepository<OrganisationInvitation, UUID> {
    Flux<OrganisationInvitation> findAllByOrganisationIdAndStatus(UUID organisationId, InvitationStatus status);
    Flux<OrganisationInvitation> findAllByInvitedEmailAndStatus(String invitedEmail, InvitationStatus status);
    Mono<OrganisationInvitation> findByIdAndOrganisationId(UUID id, UUID organisationId);
    Mono<OrganisationInvitation> findByToken(String token);
}
