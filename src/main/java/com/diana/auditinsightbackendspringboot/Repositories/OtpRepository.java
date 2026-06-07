package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.OtpVerification;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface OtpRepository extends ReactiveCrudRepository<OtpVerification, Long> {

    Mono<OtpVerification> findByEmail(String email);

    Mono<OtpVerification> findByEmailAndOtp(String email, String otp);

    Mono<Void> deleteByEmail(String email);
}