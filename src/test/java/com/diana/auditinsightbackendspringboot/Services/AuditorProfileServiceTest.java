package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.UpdateAuditorProfileRequest;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import com.diana.auditinsightbackendspringboot.Repositories.AuditorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditorProfileServiceTest {

    @Mock private AuditorRepository auditorRepository;

    private AuditorProfileService service;

    @BeforeEach
    void setUp() {
        service = new AuditorProfileService(auditorRepository);
    }

    // ──────────────────────────── getProfile ────────────────────────────

    @Test
    void getProfile_existingEmail_returnsProfile() {
        AuditorProfile profile = profile("bob@test.com");
        when(auditorRepository.findByEmailAddress("bob@test.com")).thenReturn(Mono.just(profile));

        StepVerifier.create(service.getProfile("bob@test.com"))
                .expectNextMatches(p -> "bob@test.com".equals(p.getEmailAddress()))
                .verifyComplete();
    }

    @Test
    void getProfile_unknownEmail_emitsInvalidRecord() {
        when(auditorRepository.findByEmailAddress("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(service.getProfile("nobody@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("not found"))
                .verify();
    }

    // ──────────────────────────── updateProfile ────────────────────────────

    @Test
    void updateProfile_patchCertificationNumber_savesAndReturns() {
        AuditorProfile existing = profile("bob@test.com");
        existing.setCertificationNumber(null);
        when(auditorRepository.findByEmailAddress("bob@test.com")).thenReturn(Mono.just(existing));

        AuditorProfile saved = profile("bob@test.com");
        saved.setCertificationNumber("CERT-2025");
        when(auditorRepository.save(any())).thenReturn(Mono.just(saved));

        UpdateAuditorProfileRequest req = new UpdateAuditorProfileRequest();
        req.setCertificationNumber("CERT-2025");

        StepVerifier.create(service.updateProfile("bob@test.com", req))
                .expectNextMatches(p -> "CERT-2025".equals(p.getCertificationNumber()))
                .verifyComplete();
    }

    @Test
    void updateProfile_nullFieldsNotOverwritten() {
        AuditorProfile existing = profile("bob@test.com");
        existing.setPhone("+555");
        existing.setCertificationNumber("OLD-CERT");
        when(auditorRepository.findByEmailAddress("bob@test.com")).thenReturn(Mono.just(existing));
        when(auditorRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        UpdateAuditorProfileRequest req = new UpdateAuditorProfileRequest();
        req.setFirstName("Robert");

        StepVerifier.create(service.updateProfile("bob@test.com", req))
                .expectNextMatches(p -> "+555".equals(p.getPhone())
                        && "OLD-CERT".equals(p.getCertificationNumber())
                        && "Robert".equals(p.getFirstName()))
                .verifyComplete();
    }

    @Test
    void updateProfile_unknownEmail_emitsInvalidRecord() {
        when(auditorRepository.findByEmailAddress("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(service.updateProfile("nobody@test.com", new UpdateAuditorProfileRequest()))
                .expectErrorMatches(e -> e instanceof InvalidRecord)
                .verify();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private AuditorProfile profile(String email) {
        AuditorProfile p = new AuditorProfile();
        p.setId(1L);
        p.setEmailAddress(email);
        p.setFirstName("Bob");
        p.setLastName("Jones");
        return p;
    }
}
