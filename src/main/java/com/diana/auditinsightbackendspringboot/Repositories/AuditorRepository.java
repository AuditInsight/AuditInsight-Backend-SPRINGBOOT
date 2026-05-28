package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AuditorRepository extends ReactiveCrudRepository<AuditorProfile, Long> {
    Mono<AuditorProfile> findByEmailAddress(String emailAddress);
}