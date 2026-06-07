package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Admin.AdminService;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.AuditorRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditorRepository auditorRepository;
    @Mock private EmailService emailService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, auditorRepository, emailService);
    }

    // ──────────────────────────── getPendingAuditors ────────────────────────────

    @Test
    void getPendingAuditors_returnsPendingAuditorProfiles() {
        User u = auditorUser("bob@test.com");
        when(userRepository.findAllByRoleAndVerified(Role.AUDITOR, false)).thenReturn(Flux.just(u));

        AuditorProfile profile = auditorProfile("bob@test.com");
        when(auditorRepository.findByEmailAddress("bob@test.com")).thenReturn(Mono.just(profile));

        StepVerifier.create(adminService.getPendingAuditors())
                .expectNextMatches(p -> "bob@test.com".equals(p.getEmailAddress()))
                .verifyComplete();
    }

    @Test
    void getPendingAuditors_noPendingAuditors_completesEmpty() {
        when(userRepository.findAllByRoleAndVerified(Role.AUDITOR, false)).thenReturn(Flux.empty());

        StepVerifier.create(adminService.getPendingAuditors())
                .verifyComplete();
    }

    // ──────────────────────────── approveAuditor ────────────────────────────

    @Test
    void approveAuditor_existingUnverifiedAuditor_approvesAndReturnsSuccess() {
        User u = auditorUser("bob@test.com");
        when(userRepository.findByUsername("bob@test.com")).thenReturn(Mono.just(u));
        when(userRepository.save(any())).thenReturn(Mono.just(u));

        AuditorProfile profile = auditorProfile("bob@test.com");
        when(auditorRepository.findByEmailAddress("bob@test.com")).thenReturn(Mono.just(profile));

        StepVerifier.create(adminService.approveAuditor("bob@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("approved"))
                .verifyComplete();
    }

    @Test
    void approveAuditor_alreadyVerified_returnsAlreadyApproved() {
        User u = auditorUser("bob@test.com");
        u.setVerified(true);
        when(userRepository.findByUsername("bob@test.com")).thenReturn(Mono.just(u));

        StepVerifier.create(adminService.approveAuditor("bob@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("already approved"))
                .verifyComplete();
    }

    @Test
    void approveAuditor_unknownEmail_emitsInvalidRecord() {
        when(userRepository.findByUsername("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(adminService.approveAuditor("nobody@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord)
                .verify();
    }

    @Test
    void approveAuditor_nonAuditorAccount_emitsInvalidRecord() {
        User u = auditorUser("alice@test.com");
        u.setRole(Role.CLIENT);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        StepVerifier.create(adminService.approveAuditor("alice@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("not an AUDITOR"))
                .verify();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private User auditorUser(String email) {
        User u = new User();
        u.setUsername(email);
        u.setFullName("Bob Jones");
        u.setRole(Role.AUDITOR);
        u.setVerified(false);
        u.setAuthProvider("JWT");
        return u;
    }

    private AuditorProfile auditorProfile(String email) {
        AuditorProfile p = new AuditorProfile();
        p.setEmailAddress(email);
        p.setFirstName("Bob");
        p.setLastName("Jones");
        return p;
    }
}
