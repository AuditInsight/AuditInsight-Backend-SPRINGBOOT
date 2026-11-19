package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.SigningVerification;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.SigningVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SigningVerificationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock private SigningVerificationRepository repo;
    @Mock private EmailService emailService;

    private SigningVerificationService service;
    private final Long userId = 7L;
    private final UUID evidenceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SigningVerificationService(repo, emailService, CLOCK);
    }

    private SigningVerification code(String plain) {
        SigningVerification v = new SigningVerification();
        v.setId(UUID.randomUUID());
        v.setUserId(userId);
        v.setEvidenceId(evidenceId);
        v.setCodeHash(SigningVerificationService.hash(userId, evidenceId, plain));
        v.setCreatedAt(NOW.minusMinutes(2));
        v.setExpiresAt(NOW.plusMinutes(8));
        return v;
    }

    private static void expectError(Mono<?> mono, String messagePart) {
        StepVerifier.create(mono)
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains(messagePart))
                .verify();
    }

    @Test
    void sendCode_savesHashedCodeAndEmailsIt() {
        User user = new User();
        user.setId(userId);
        user.setUsername("jane@example.com");
        user.setFullName("Jane");
        Evidence ev = new Evidence();
        ev.setId(evidenceId);
        ev.setDocumentName("Contract.pdf");
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.empty());
        when(repo.countByUserIdAndCreatedAtAfter(eq(userId), any())).thenReturn(Mono.just(0L));
        when(repo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.sendCode(user, ev))
                .assertNext(sent -> {
                    assertEquals("j***@example.com", sent.sentTo());
                    assertEquals(600, sent.expiresInSeconds());
                })
                .verifyComplete();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(2000)).sendSigningVerificationEmail(
                eq("jane@example.com"), eq("Jane"), eq("Contract.pdf"), codeCaptor.capture());
        ArgumentCaptor<SigningVerification> saved = ArgumentCaptor.forClass(SigningVerification.class);
        verify(repo).save(saved.capture());
        String code = codeCaptor.getValue();
        assertEquals(6, code.length());
        assertNotEquals(code, saved.getValue().getCodeHash());
        assertEquals(SigningVerificationService.hash(userId, evidenceId, code), saved.getValue().getCodeHash());
        assertEquals(NOW.plusMinutes(10), saved.getValue().getExpiresAt());
    }

    @Test
    void sendCode_refusesWithinCooldown() {
        User user = new User();
        user.setId(userId);
        Evidence ev = new Evidence();
        ev.setId(evidenceId);
        SigningVerification recent = code("123456");
        recent.setCreatedAt(NOW.minusSeconds(20));
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.just(recent));

        expectError(service.sendCode(user, ev), "wait 40 seconds");
        verify(repo, never()).save(any());
    }

    @Test
    void sendCode_refusesOverHourlyLimit() {
        User user = new User();
        user.setId(userId);
        Evidence ev = new Evidence();
        ev.setId(evidenceId);
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.empty());
        when(repo.countByUserIdAndCreatedAtAfter(eq(userId), any())).thenReturn(Mono.just(10L));

        expectError(service.sendCode(user, ev), "Too many verification codes");
        verify(repo, never()).save(any());
    }

    @Test
    void claim_correctCodeIsConsumed() {
        SigningVerification v = code("123456");
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.just(v));
        when(repo.consumeIfUnused(v.getId(), NOW)).thenReturn(Mono.just(1));

        StepVerifier.create(service.claim(userId, evidenceId, " 123456 ")).expectNext(v.getId()).verifyComplete();
    }

    @Test
    void claim_missingCodeIsRejectedWithoutLookup() {
        expectError(service.claim(userId, evidenceId, null), "6-digit verification code");
        verify(repo, never()).findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void claim_noCodeRequested() {
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.empty());
        expectError(service.claim(userId, evidenceId, "123456"), "Request a code first");
    }

    @Test
    void claim_wrongCodeCountsAttempt() {
        SigningVerification v = code("123456");
        v.setAttempts(1);
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.just(v));
        when(repo.incrementAttempts(v.getId())).thenReturn(Mono.just(1));

        expectError(service.claim(userId, evidenceId, "654321"), "3 attempts left");
        verify(repo).incrementAttempts(v.getId());
        verify(repo, never()).consumeIfUnused(any(), any());
    }

    @Test
    void claim_lockedAfterMaxAttemptsEvenWithRightCode() {
        SigningVerification v = code("123456");
        v.setAttempts(SigningVerificationService.MAX_ATTEMPTS);
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.just(v));
        expectError(service.claim(userId, evidenceId, "123456"), "Too many incorrect attempts");
    }

    @Test
    void claim_expiredCode() {
        SigningVerification v = code("123456");
        v.setExpiresAt(NOW.minusSeconds(1));
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.just(v));
        expectError(service.claim(userId, evidenceId, "123456"), "expired");
    }

    @Test
    void claim_alreadyUsedCode() {
        SigningVerification v = code("123456");
        v.setConsumedAt(NOW.minusMinutes(1));
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.just(v));
        expectError(service.claim(userId, evidenceId, "123456"), "already been used");
    }

    @Test
    void claim_concurrentUseLosesRace() {
        SigningVerification v = code("123456");
        when(repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)).thenReturn(Mono.just(v));
        when(repo.consumeIfUnused(v.getId(), NOW)).thenReturn(Mono.just(0));
        expectError(service.claim(userId, evidenceId, "123456"), "already been used");
    }

    @Test
    void hash_isBoundToUserAndDocument() {
        assertNotEquals(SigningVerificationService.hash(1L, evidenceId, "123456"),
                SigningVerificationService.hash(2L, evidenceId, "123456"));
        assertNotEquals(SigningVerificationService.hash(1L, evidenceId, "123456"),
                SigningVerificationService.hash(1L, UUID.randomUUID(), "123456"));
    }
}
