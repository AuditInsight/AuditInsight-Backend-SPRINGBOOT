package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.SigningVerification;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.SigningVerificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Email re-verification before signing: every signature needs a fresh one-time code that was emailed to the
 * signer for that specific document. This proves the person signing still controls the account's email address,
 * not just a logged-in browser session.
 */
@Service
public class SigningVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_HOUR = 10;

    private final SigningVerificationRepository repo;
    private final EmailService emailService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SigningVerificationService(SigningVerificationRepository repo, EmailService emailService) {
        this(repo, emailService, Clock.systemDefaultZone());
    }

    SigningVerificationService(SigningVerificationRepository repo, EmailService emailService, Clock clock) {
        this.repo = repo;
        this.emailService = emailService;
        this.clock = clock;
    }

    /** What the app shows after a code is sent. */
    public record CodeSent(String message, String sentTo, long expiresInSeconds, long resendAfterSeconds) {}

    /** Creates a new code for this user and document and emails it. Any earlier code for the document stops working. */
    public Mono<CodeSent> sendCode(User user, Evidence evidence) {
        LocalDateTime now = LocalDateTime.now(clock);
        return repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(user.getId(), evidence.getId())
                .filter(last -> last.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN)))
                .flatMap(last -> {
                    long wait = Math.max(1, Duration.between(now, last.getCreatedAt().plus(RESEND_COOLDOWN)).toSeconds());
                    return Mono.<CodeSent>error(new InvalidRecord(
                            "A code was just sent. Please wait " + wait + " seconds before asking for another."));
                })
                .switchIfEmpty(Mono.defer(() -> repo.countByUserIdAndCreatedAtAfter(user.getId(), now.minusHours(1))
                        .flatMap(count -> {
                            if (count >= MAX_CODES_PER_HOUR) {
                                return Mono.error(new InvalidRecord(
                                        "Too many verification codes requested. Please try again later."));
                            }
                            String code = String.format("%06d", random.nextInt(1_000_000));
                            SigningVerification v = new SigningVerification();
                            v.setUserId(user.getId());
                            v.setEvidenceId(evidence.getId());
                            v.setCodeHash(hash(user.getId(), evidence.getId(), code));
                            v.setAttempts(0);
                            v.setCreatedAt(now);
                            v.setExpiresAt(now.plus(CODE_TTL));
                            return repo.save(v)
                                    .then(Mono.fromRunnable(() -> emailService.sendSigningVerificationEmail(
                                                    user.getUsername(), user.getFullName(), evidence.getDocumentName(), code))
                                            .subscribeOn(Schedulers.boundedElastic()))
                                    .thenReturn(new CodeSent("A verification code has been sent to your email.",
                                            maskEmail(user.getUsername()), CODE_TTL.toSeconds(),
                                            RESEND_COOLDOWN.toSeconds()));
                        })));
    }

    /**
     * Checks the code and marks it used, returning the code's id so it can be {@link #release released} if the
     * signature then fails to save. Wrong codes count towards {@link #MAX_ATTEMPTS}.
     */
    public Mono<UUID> claim(Long userId, UUID evidenceId, String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            return Mono.<UUID>error(new InvalidRecord(
                    "Enter the 6-digit verification code sent to your email before signing."));
        }
        String entered = code.trim();
        LocalDateTime now = LocalDateTime.now(clock);
        return repo.findFirstByUserIdAndEvidenceIdOrderByCreatedAtDesc(userId, evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord(
                        "No verification code was requested for this document. Request a code first.")))
                .flatMap(v -> {
                    if (v.getConsumedAt() != null) {
                        return Mono.<UUID>error(new InvalidRecord("This code has already been used. Request a new code."));
                    }
                    if (v.getExpiresAt().isBefore(now)) {
                        return Mono.<UUID>error(new InvalidRecord("This code has expired. Request a new code."));
                    }
                    if (v.getAttempts() >= MAX_ATTEMPTS) {
                        return Mono.<UUID>error(new InvalidRecord("Too many incorrect attempts. Request a new code."));
                    }
                    if (!MessageDigest.isEqual(
                            hash(userId, evidenceId, entered).getBytes(StandardCharsets.US_ASCII),
                            v.getCodeHash().getBytes(StandardCharsets.US_ASCII))) {
                        int left = MAX_ATTEMPTS - v.getAttempts() - 1;
                        return repo.incrementAttempts(v.getId()).then(Mono.<UUID>error(new InvalidRecord(left > 0
                                ? "Incorrect code. " + left + (left == 1 ? " attempt" : " attempts") + " left."
                                : "Incorrect code. Request a new code.")));
                    }
                    return repo.consumeIfUnused(v.getId(), now).flatMap(updated -> updated == 1
                            ? Mono.just(v.getId())
                            : Mono.<UUID>error(new InvalidRecord("This code has already been used. Request a new code.")));
                });
    }

    /** Makes a claimed code usable again (the signature it was claimed for was not saved). */
    public Mono<Void> release(UUID verificationId) {
        return repo.release(verificationId).then();
    }

    static String hash(Long userId, UUID evidenceId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + evidenceId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** j***@example.com — enough for the user to recognise the inbox without echoing the full address. */
    static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
