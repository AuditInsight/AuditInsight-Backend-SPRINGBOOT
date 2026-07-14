package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Admin.AdminService;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, emailService);
    }

    // ──────────────────────────── activateAccount ────────────────────────────

    @Test
    void activateAccount_inactiveAuditor_activatesAndReturnsSuccess() {
        User u = accountUser("bob@test.com", Role.AUDITOR);
        u.setActive(false);
        when(userRepository.findByUsername("bob@test.com")).thenReturn(Mono.just(u));
        when(userRepository.save(any())).thenReturn(Mono.just(u));

        StepVerifier.create(adminService.activateAccount("bob@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("activated"))
                .verifyComplete();
    }

    @Test
    void activateAccount_alreadyActive_returnsAlreadyActive() {
        User u = accountUser("bob@test.com", Role.AUDITOR);
        when(userRepository.findByUsername("bob@test.com")).thenReturn(Mono.just(u));

        StepVerifier.create(adminService.activateAccount("bob@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("already active"))
                .verifyComplete();
    }

    @Test
    void activateAccount_unknownEmail_emitsInvalidRecord() {
        when(userRepository.findByUsername("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(adminService.activateAccount("nobody@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord)
                .verify();
    }

    // ──────────────────────────── deactivateAccount ────────────────────────────

    @Test
    void deactivateAccount_activeClient_deactivatesAndReturnsSuccess() {
        User u = accountUser("alice@test.com", Role.CLIENT);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));
        when(userRepository.save(any())).thenReturn(Mono.just(u));

        StepVerifier.create(adminService.deactivateAccount("alice@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("deactivated"))
                .verifyComplete();
    }

    @Test
    void deactivateAccount_alreadyInactive_returnsAlreadyInactive() {
        User u = accountUser("alice@test.com", Role.CLIENT);
        u.setActive(false);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        StepVerifier.create(adminService.deactivateAccount("alice@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("already inactive"))
                .verifyComplete();
    }

    @Test
    void deactivateAccount_adminAccount_emitsInvalidRecord() {
        User u = accountUser("admin@test.com", Role.ADMIN);
        when(userRepository.findByUsername("admin@test.com")).thenReturn(Mono.just(u));

        StepVerifier.create(adminService.deactivateAccount("admin@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("cannot be deactivated"))
                .verify();
    }

    @Test
    void deactivateAccount_unknownEmail_emitsInvalidRecord() {
        when(userRepository.findByUsername("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(adminService.deactivateAccount("nobody@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord)
                .verify();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private User accountUser(String email, Role role) {
        User u = new User();
        u.setUsername(email);
        u.setFullName("Test User");
        u.setRole(role);
        u.setVerified(true);
        u.setAuthProvider("JWT");
        return u;
    }
}
