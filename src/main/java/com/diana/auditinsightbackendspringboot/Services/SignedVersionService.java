package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceDocumentVersionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceSignatureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores every signed rendition of an evidence document so Preview and Download serve the exact same bytes.
 * <p>
 * The original upload stays on {@link Evidence#getFileUpload()} (version 0). A stored version is "current" when it
 * contains every signature the document has; otherwise a new one is rendered, uploaded and saved as the next
 * version. Only one version is ever kept per signature count: when two requests race to create the same version,
 * the loser adopts the winner's version instead of saving a second, byte-different copy.
 */
@Service
@Slf4j
public class SignedVersionService {

    /** A stored signed version together with its bytes, ready to send to the client. */
    public record ServedVersion(UUID versionId, int versionNo, String sha256, String fileName, byte[] bytes) {}

    private static final String SIGNED_FOLDER = "signed";

    private final EvidenceRepository evidenceRepo;
    private final EvidenceSignatureRepository signatureRepo;
    private final EvidenceDocumentVersionRepository versionRepo;
    private final SignedDocumentService signedDocumentService;
    private final CloudinaryService cloudinaryService;
    private final OrgAccessService orgAccessService;

    public SignedVersionService(EvidenceRepository evidenceRepo,
                                EvidenceSignatureRepository signatureRepo,
                                EvidenceDocumentVersionRepository versionRepo,
                                SignedDocumentService signedDocumentService,
                                CloudinaryService cloudinaryService,
                                OrgAccessService orgAccessService) {
        this.evidenceRepo = evidenceRepo;
        this.signatureRepo = signatureRepo;
        this.versionRepo = versionRepo;
        this.signedDocumentService = signedDocumentService;
        this.cloudinaryService = cloudinaryService;
        this.orgAccessService = orgAccessService;
    }

    /** Preview/Download entry point: checks access, then serves the current stored version (creating it if needed). */
    public Mono<ServedVersion> serve(String requesterEmail, UUID evidenceId) {
        return evidenceRepo.findById(evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Evidence not found")))
                .flatMap(ev -> orgAccessService.resolveGatedMember(ev.getOrganisationId(), requesterEmail)
                        .flatMap(ctx -> currentVersion(ev, ctx.user().getId())));
    }

    /**
     * Called after a signature is saved. Runs in the background so signing stays fast; if it fails or hasn't
     * finished yet, the next Preview/Download creates the version instead. Links the signature to the version
     * that first contains it.
     */
    public void createVersionAfterSigning(Evidence ev, UUID signatureId, Long signerId) {
        latestCoveringOrCreate(ev, signerId)
                .flatMap(version -> signatureRepo.findById(signatureId)
                        .flatMap(sig -> {
                            sig.setDocumentVersionId(version.getId());
                            return signatureRepo.save(sig);
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        saved -> log.info("Stored signed version for evidence {} after signature {}", ev.getId(), signatureId),
                        e -> log.warn("Could not store the signed version for evidence {} yet (it will be created on the "
                                + "next preview/download): {}", ev.getId(), e.getMessage()));
    }

    Mono<ServedVersion> currentVersion(Evidence ev, Long actorId) {
        return signatureRepo.countByEvidenceId(ev.getId())
                .defaultIfEmpty(0L)
                .flatMap(count -> {
                    if (count == 0) {
                        return Mono.error(new InvalidRecord(
                                "This document has not been signed yet, so there is no signed version to show"));
                    }
                    return versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(ev.getId())
                            .filter(v -> v.getSignatureCount() >= count)
                            .flatMap(this::load)
                            .switchIfEmpty(Mono.defer(() -> createVersion(ev, actorId)));
                });
    }

    /** The latest version if it already has every signature; otherwise a newly created one. Metadata only. */
    private Mono<EvidenceDocumentVersion> latestCoveringOrCreate(Evidence ev, Long actorId) {
        return signatureRepo.countByEvidenceId(ev.getId())
                .defaultIfEmpty(0L)
                .flatMap(count -> versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(ev.getId())
                        .filter(v -> v.getSignatureCount() >= count))
                .switchIfEmpty(Mono.defer(() -> createVersion(ev, actorId)
                        .flatMap(served -> versionRepo.findById(served.versionId()))));
    }

    /**
     * The next rendition. PDFs and images build on the latest stored version (or version 0 when there is none) and
     * stamp only the signatures it doesn't contain yet — a version with signature_count k holds the first k
     * signatures in signing order — so earlier stamps never move. Word/Excel certificates are re-rendered whole.
     */
    private Mono<SignedDocumentService.SignedDocument> renderNext(Evidence ev) {
        if (!SignedDocumentService.isStampable(ev.getFileType())) {
            return signedDocumentService.renderSigned(ev);
        }
        return versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(ev.getId())
                .flatMap(latest -> load(latest).map(served -> new Base(served.bytes(), latest.getSignatureCount())))
                .switchIfEmpty(Mono.defer(() -> signedDocumentService.baseDocument(ev).map(bytes -> new Base(bytes, 0))))
                .flatMap(base -> signedDocumentService.stampOnto(ev, base.bytes(), base.signaturesIncluded()));
    }

    private record Base(byte[] bytes, int signaturesIncluded) {}

    /**
     * The exact document a signer should place their signature on: the current signed version, or version 0 (the
     * original PDF, or the image on its A4 page) when nobody has signed yet. Its SHA-256 is sent back with the
     * signature as a record of what the signer saw.
     */
    public Mono<ServedVersion> signingBase(String requesterEmail, UUID evidenceId) {
        return evidenceRepo.findById(evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Evidence not found")))
                .flatMap(ev -> orgAccessService.resolveGatedMember(ev.getOrganisationId(), requesterEmail)
                        .filter(ctx -> SignedDocumentService.isStampable(ev.getFileType()))
                        .switchIfEmpty(Mono.error(new InvalidRecord("Word and Excel files can't have a signature "
                                + "placed on them; sign them to get a signature certificate")))
                        .flatMap(ctx -> signatureRepo.countByEvidenceId(ev.getId())
                                .defaultIfEmpty(0L)
                                .flatMap(count -> count > 0
                                        ? currentVersion(ev, ctx.user().getId())
                                        : signedDocumentService.baseDocument(ev).map(bytes -> new ServedVersion(
                                                null, 0, sha256Hex(bytes), ev.getDocumentName(), bytes)))));
    }

    /** Fetches a stored version's bytes and checks they still match the recorded SHA-256. */
    private Mono<ServedVersion> load(EvidenceDocumentVersion v) {
        return cloudinaryService.downloadOriginal(v.getFileUrl())
                .onErrorMap(e -> !(e instanceof InvalidRecord),
                        e -> new InvalidRecord("Could not fetch the signed document: " + e.getMessage()))
                .flatMap(bytes -> {
                    if (!sha256Hex(bytes).equals(v.getSha256())) {
                        log.error("Stored signed version {} of evidence {} does not match its recorded SHA-256",
                                v.getVersionNo(), v.getEvidenceId());
                        return Mono.error(new InvalidRecord(
                                "The stored signed document failed its integrity check. Please contact support."));
                    }
                    return Mono.just(served(v, bytes));
                });
    }

    /** Renders the document with all current signatures, uploads it and records it as the next version. */
    private Mono<ServedVersion> createVersion(Evidence ev, Long actorId) {
        return renderNext(ev)
                .flatMap(doc -> cloudinaryService.upload(doc.bytes(), ev.getOrganisationId() + "/" + SIGNED_FOLDER, "pdf")
                        .flatMap(url -> saveNextVersion(ev, actorId, doc, url)
                                // Two requests computed the same version number: adopt whatever won.
                                .retryWhen(Retry.max(3).filter(e -> e instanceof DataIntegrityViolationException)))
                        .flatMap(saved -> saved.getSha256().equals(sha256Hex(doc.bytes()))
                                ? Mono.just(served(saved, doc.bytes()))
                                : load(saved)));
    }

    /**
     * Saves our rendition as version N+1 — unless a version with at least as many signatures appeared while we were
     * rendering, in which case that one is returned and ours is dropped, keeping one version per signature state.
     */
    private Mono<EvidenceDocumentVersion> saveNextVersion(Evidence ev, Long actorId,
                                                          SignedDocumentService.SignedDocument doc, String url) {
        return Mono.defer(() -> versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(ev.getId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(latest -> {
                    if (latest.isPresent() && latest.get().getSignatureCount() >= doc.signatureCount()) {
                        return Mono.just(latest.get());
                    }
                    EvidenceDocumentVersion v = new EvidenceDocumentVersion();
                    v.setEvidenceId(ev.getId());
                    v.setOrganisationId(ev.getOrganisationId());
                    v.setVersionNo(latest.map(l -> l.getVersionNo() + 1).orElse(1));
                    v.setFileUrl(url);
                    v.setFileName(doc.fileName());
                    v.setSha256(sha256Hex(doc.bytes()));
                    v.setSizeBytes((long) doc.bytes().length);
                    v.setSignatureCount(doc.signatureCount());
                    v.setCreatedBy(actorId);
                    v.setCreatedAt(LocalDateTime.now());
                    return versionRepo.save(v);
                }));
    }

    private ServedVersion served(EvidenceDocumentVersion v, byte[] bytes) {
        return new ServedVersion(v.getId(), v.getVersionNo(), v.getSha256(), v.getFileName(), bytes);
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
