package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.EvidenceSignaturesResponse;
import com.diana.auditinsightbackendspringboot.DTOs.RequestSignatureRequest;
import com.diana.auditinsightbackendspringboot.DTOs.SignEvidenceRequest;
import com.diana.auditinsightbackendspringboot.DTOs.SignatureRequestResponse;
import com.diana.auditinsightbackendspringboot.DTOs.SignatureResponse;
import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.SignatureRequestStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceSignature;
import com.diana.auditinsightbackendspringboot.Models.SignatureRequest;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceSignatureRepository;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SignatureRequestRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional e-signatures on evidence documents. Signing is voluntary: any active member of the
 * evidence's organisation can sign it whether or not anyone asked them to. A signature request is
 * only a nudge to a specific member; signing a document you were asked about also closes the request.
 */
@Service
public class SignatureService {

    private static final String PNG_DATA_URL_PREFIX = "data:image/png;base64,";

    private final EvidenceRepository evidenceRepo;
    private final EvidenceSignatureRepository signatureRepo;
    private final SignatureRequestRepository requestRepo;
    private final OrganisationMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final OrgAccessService orgAccessService;
    private final EmailService emailService;

    public SignatureService(EvidenceRepository evidenceRepo,
                            EvidenceSignatureRepository signatureRepo,
                            SignatureRequestRepository requestRepo,
                            OrganisationMemberRepository memberRepo,
                            UserRepository userRepo,
                            OrgAccessService orgAccessService,
                            EmailService emailService) {
        this.evidenceRepo = evidenceRepo;
        this.signatureRepo = signatureRepo;
        this.requestRepo = requestRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.orgAccessService = orgAccessService;
        this.emailService = emailService;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private Mono<Evidence> findEvidence(UUID evidenceId) {
        return evidenceRepo.findById(evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Evidence not found")));
    }

    // ── Sign (voluntary — no prior request needed) ────────────────────────────────

    public Mono<SignatureResponse> sign(String email, UUID evidenceId, SignEvidenceRequest body) {
        String typedName = body.getTypedName() == null || body.getTypedName().isBlank()
                ? null : body.getTypedName().trim();
        String fullName = blankToNull(body.getFullName());
        String title = blankToNull(body.getTitle());
        String image = body.getSignatureImage() == null || body.getSignatureImage().isBlank()
                ? null : body.getSignatureImage();
        if (image != null && !image.startsWith(PNG_DATA_URL_PREFIX)) {
            return Mono.error(new InvalidRecord("signatureImage must be a PNG data URL"));
        }

        return findEvidence(evidenceId).flatMap(ev ->
                orgAccessService.resolveGatedMember(ev.getOrganisationId(), email).flatMap(ctx -> {
                    User user = ctx.user();
                    return signatureRepo.findByEvidenceIdAndSignerId(evidenceId, user.getId())
                            .flatMap(existing -> Mono.<SignatureResponse>error(
                                    new InvalidRecord("You have already signed this document")))
                            .switchIfEmpty(Mono.defer(() -> persistSignature(ev, user, fullName, title, typedName, image)));
                }));
    }

    private Mono<SignatureResponse> persistSignature(Evidence ev, User user, String fullName, String title,
                                                     String typedName, String image) {
        return requestRepo.findByEvidenceIdAndRequestedUserIdAndStatus(
                        ev.getId(), user.getId(), SignatureRequestStatus.PENDING)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(pending -> {
                    LocalDateTime now = LocalDateTime.now();
                    EvidenceSignature s = new EvidenceSignature();
                    s.setEvidenceId(ev.getId());
                    s.setOrganisationId(ev.getOrganisationId());
                    s.setSignerId(user.getId());
                    s.setSignerName(fullName != null ? fullName : user.getFullName());
                    s.setSignerTitle(title);
                    s.setTypedName(typedName);
                    s.setSignatureImage(image);
                    s.setSignedAt(now);
                    s.setRequestId(pending.map(SignatureRequest::getId).orElse(null));
                    return signatureRepo.save(s).flatMap(saved -> pending
                            .map(req -> {
                                req.setStatus(SignatureRequestStatus.SIGNED);
                                req.setRespondedAt(now);
                                return requestRepo.save(req).thenReturn(saved);
                            })
                            .orElseGet(() -> Mono.just(saved)));
                })
                .onErrorMap(DataIntegrityViolationException.class,
                        e -> new InvalidRecord("You have already signed this document"))
                .map(this::toResponse);
    }

    // ── Request signatures from other members ─────────────────────────────────────

    public Mono<List<SignatureRequestResponse>> requestSignatures(String email, UUID evidenceId,
                                                                  RequestSignatureRequest body) {
        return findEvidence(evidenceId).flatMap(ev ->
                orgAccessService.resolveGatedMember(ev.getOrganisationId(), email).flatMap(ctx ->
                        resolveTargetIds(body).flatMap(ids ->
                                Flux.fromIterable(ids)
                                        .concatMap(uid -> createRequest(ev, ctx.user(), uid, body.getMessage()))
                                        .map(r -> toResponse(r, ev.getDocumentName()))
                                        .collectList())));
    }

    /** Merges the picked member ids with the typed emails (resolved to user ids), de-duplicated. */
    private Mono<LinkedHashSet<Long>> resolveTargetIds(RequestSignatureRequest body) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (body.getUserIds() != null) ids.addAll(body.getUserIds());
        List<String> emails = body.getEmails() == null ? List.of() : body.getEmails();
        if (ids.isEmpty() && emails.isEmpty()) {
            return Mono.error(new InvalidRecord("select at least one member or enter an email address"));
        }
        return Flux.fromIterable(new LinkedHashSet<>(emails))
                .concatMap(e -> userRepo.findByUsername(e.trim())
                        .switchIfEmpty(Mono.error(new InvalidRecord(
                                e.trim() + " is not a member of this organisation"))))
                .map(User::getId)
                .collectList()
                .map(resolved -> {
                    ids.addAll(resolved);
                    return ids;
                });
    }

    private Mono<SignatureRequest> createRequest(Evidence ev, User requester, Long targetId, String message) {
        if (targetId.equals(requester.getId())) {
            return Mono.error(new InvalidRecord("You cannot request your own signature; just sign the document"));
        }
        return memberRepo.findByOrganisationIdAndUserId(ev.getOrganisationId(), targetId)
                .switchIfEmpty(Mono.error(new InvalidRecord("User " + targetId + " is not a member of this organisation")))
                .flatMap(member -> member.getStatus() != MemberStatus.ACTIVE
                        ? Mono.<User>error(new InvalidRecord("User " + targetId + " is not an active member"))
                        : userRepo.findById(targetId))
                .switchIfEmpty(Mono.error(new InvalidRecord("User " + targetId + " not found")))
                .flatMap(target -> signatureRepo.findByEvidenceIdAndSignerId(ev.getId(), targetId)
                        .flatMap(s -> Mono.<SignatureRequest>error(
                                new InvalidRecord(target.getFullName() + " has already signed this document")))
                        .switchIfEmpty(Mono.defer(() -> requestRepo
                                .findByEvidenceIdAndRequestedUserIdAndStatus(ev.getId(), targetId, SignatureRequestStatus.PENDING)
                                .flatMap(r -> Mono.<SignatureRequest>error(new InvalidRecord(
                                        target.getFullName() + " has already been asked to sign this document")))
                                .switchIfEmpty(Mono.defer(() -> {
                                    SignatureRequest r = new SignatureRequest();
                                    r.setEvidenceId(ev.getId());
                                    r.setOrganisationId(ev.getOrganisationId());
                                    r.setRequestedBy(requester.getId());
                                    r.setRequestedByName(requester.getFullName());
                                    r.setRequestedUserId(targetId);
                                    r.setRequestedUserName(target.getFullName());
                                    r.setMessage(message == null || message.isBlank() ? null : message.trim());
                                    r.setStatus(SignatureRequestStatus.PENDING);
                                    r.setCreatedAt(LocalDateTime.now());
                                    return requestRepo.save(r).doOnSuccess(saved ->
                                            Mono.fromRunnable(() -> emailService.sendSignatureRequestEmail(
                                                            target.getUsername(), target.getFullName(),
                                                            requester.getFullName(), ev.getDocumentName(),
                                                            saved.getMessage()))
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .subscribe());
                                })))));
    }

    // ── Read ──────────────────────────────────────────────────────────────────────

    public Mono<EvidenceSignaturesResponse> list(String email, UUID evidenceId) {
        return findEvidence(evidenceId).flatMap(ev ->
                orgAccessService.resolveGatedMember(ev.getOrganisationId(), email).flatMap(ctx ->
                        Mono.zip(
                                signatureRepo.findAllByEvidenceIdOrderBySignedAtAsc(evidenceId)
                                        .map(this::toResponse).collectList(),
                                requestRepo.findAllByEvidenceIdAndStatus(evidenceId, SignatureRequestStatus.PENDING)
                                        .map(r -> toResponse(r, ev.getDocumentName())).collectList())
                                .map(t -> {
                                    Long me = ctx.user().getId();
                                    boolean signedByMe = t.getT1().stream().anyMatch(s -> me.equals(s.getSignerId()));
                                    SignatureRequestResponse mine = t.getT2().stream()
                                            .filter(r -> me.equals(r.getRequestedUserId()))
                                            .findFirst().orElse(null);
                                    return new EvidenceSignaturesResponse(t.getT1(), t.getT2(), signedByMe, mine);
                                })));
    }

    /** Signature requests addressed to the caller that are still waiting. */
    public Flux<SignatureRequestResponse> pendingForMe(String email) {
        return userRepo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMapMany(user -> requestRepo.findAllByRequestedUserIdAndStatusOrderByCreatedAtDesc(
                        user.getId(), SignatureRequestStatus.PENDING))
                .concatMap(r -> evidenceRepo.findById(r.getEvidenceId())
                        .map(Evidence::getDocumentName)
                        .defaultIfEmpty("Document")
                        .map(name -> toResponse(r, name)));
    }

    // ── Decline ───────────────────────────────────────────────────────────────────

    public Mono<SignatureRequestResponse> decline(String email, UUID requestId) {
        return userRepo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMap(user -> requestRepo.findById(requestId)
                        .switchIfEmpty(Mono.error(new InvalidRecord("Signature request not found")))
                        .flatMap(req -> {
                            if (!req.getRequestedUserId().equals(user.getId())) {
                                return Mono.error(new ForbiddenException("This signature request is not addressed to you"));
                            }
                            if (req.getStatus() != SignatureRequestStatus.PENDING) {
                                return Mono.error(new InvalidRecord("This request has already been " + req.getStatus().name().toLowerCase()));
                            }
                            req.setStatus(SignatureRequestStatus.DECLINED);
                            req.setRespondedAt(LocalDateTime.now());
                            return requestRepo.save(req);
                        }))
                .flatMap(saved -> evidenceRepo.findById(saved.getEvidenceId())
                        .map(Evidence::getDocumentName)
                        .defaultIfEmpty("Document")
                        .map(name -> toResponse(saved, name)));
    }

    // ── Mapping ───────────────────────────────────────────────────────────────────

    private SignatureResponse toResponse(EvidenceSignature s) {
        SignatureResponse r = new SignatureResponse();
        r.setId(s.getId());
        r.setEvidenceId(s.getEvidenceId());
        r.setSignerId(s.getSignerId());
        r.setSignerName(s.getSignerName());
        r.setSignerTitle(s.getSignerTitle());
        r.setTypedName(s.getTypedName());
        r.setSignatureImage(s.getSignatureImage());
        r.setSignedAt(s.getSignedAt());
        r.setRequested(s.getRequestId() != null);
        return r;
    }

    private SignatureRequestResponse toResponse(SignatureRequest q, String documentName) {
        SignatureRequestResponse r = new SignatureRequestResponse();
        r.setId(q.getId());
        r.setEvidenceId(q.getEvidenceId());
        r.setDocumentName(documentName);
        r.setOrganisationId(q.getOrganisationId());
        r.setRequestedBy(q.getRequestedBy());
        r.setRequestedByName(q.getRequestedByName());
        r.setRequestedUserId(q.getRequestedUserId());
        r.setRequestedUserName(q.getRequestedUserName());
        r.setMessage(q.getMessage());
        r.setStatus(q.getStatus());
        r.setCreatedAt(q.getCreatedAt());
        r.setRespondedAt(q.getRespondedAt());
        return r;
    }
}
