package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.UpdateClientProfileRequest;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.ClientProfile;
import com.diana.auditinsightbackendspringboot.Repositories.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientProfileServiceTest {

    @Mock private ClientRepository clientRepository;

    private ClientProfileService service;

    @BeforeEach
    void setUp() {
        service = new ClientProfileService(clientRepository);
    }

    // ──────────────────────────── getProfile ────────────────────────────

    @Test
    void getProfile_existingEmail_returnsProfile() {
        ClientProfile profile = profile("alice@test.com");
        when(clientRepository.findByEmailAddress("alice@test.com")).thenReturn(Mono.just(profile));

        StepVerifier.create(service.getProfile("alice@test.com"))
                .expectNextMatches(p -> "alice@test.com".equals(p.getEmailAddress()))
                .verifyComplete();
    }

    @Test
    void getProfile_unknownEmail_emitsInvalidRecord() {
        when(clientRepository.findByEmailAddress("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(service.getProfile("nobody@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("not found"))
                .verify();
    }

    // ──────────────────────────── updateProfile ────────────────────────────

    @Test
    void updateProfile_patchPhone_savesAndReturnsUpdated() {
        ClientProfile existing = profile("alice@test.com");
        existing.setPhone(null);
        when(clientRepository.findByEmailAddress("alice@test.com")).thenReturn(Mono.just(existing));

        ClientProfile saved = profile("alice@test.com");
        saved.setPhone("+1234567890");
        when(clientRepository.save(any())).thenReturn(Mono.just(saved));

        UpdateClientProfileRequest req = new UpdateClientProfileRequest();
        req.setPhone("+1234567890");

        StepVerifier.create(service.updateProfile("alice@test.com", req))
                .expectNextMatches(p -> "+1234567890".equals(p.getPhone()))
                .verifyComplete();
    }

    @Test
    void updateProfile_nameFieldsProtectedFromNull_otherFieldsAlwaysSet() {
        ClientProfile existing = profile("alice@test.com");
        existing.setFirstName("Alice");
        existing.setPhone("+999");
        when(clientRepository.findByEmailAddress("alice@test.com")).thenReturn(Mono.just(existing));
        when(clientRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        UpdateClientProfileRequest req = new UpdateClientProfileRequest();
        // firstName null → protected, stays "Alice"
        // phone/companyName null → overwritten to null (intentional partial-update design)
        req.setAddress("123 Main St");

        StepVerifier.create(service.updateProfile("alice@test.com", req))
                .expectNextMatches(p -> "Alice".equals(p.getFirstName())
                        && p.getPhone() == null
                        && "123 Main St".equals(p.getAddress()))
                .verifyComplete();
    }

    @Test
    void updateProfile_unknownEmail_emitsInvalidRecord() {
        when(clientRepository.findByEmailAddress("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(service.updateProfile("nobody@test.com", new UpdateClientProfileRequest()))
                .expectErrorMatches(e -> e instanceof InvalidRecord)
                .verify();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private ClientProfile profile(String email) {
        ClientProfile p = new ClientProfile();
        p.setId(UUID.randomUUID());
        p.setEmailAddress(email);
        p.setFirstName("Alice");
        p.setLastName("Smith");
        return p;
    }
}
