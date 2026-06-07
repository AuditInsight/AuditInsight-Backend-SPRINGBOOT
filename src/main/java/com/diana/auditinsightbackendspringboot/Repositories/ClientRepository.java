package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.ClientProfile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ClientRepository extends ReactiveCrudRepository<ClientProfile, UUID> {
    Mono<ClientProfile> findByEmailAddress(String emailAddress);
}
