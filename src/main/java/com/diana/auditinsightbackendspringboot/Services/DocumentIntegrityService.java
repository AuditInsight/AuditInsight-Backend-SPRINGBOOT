package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.DocumentVersionsResponse;
import com.diana.auditinsightbackendspringboot.DTOs.RequestMeta;
import com.diana.auditinsightbackendspringboot.DTOs.VerificationResponse;
import com.diana.auditinsightbackendspringboot.Enum.AuditEventType;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceDocumentVersionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceSignatureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Integrity features for evidence documents:
 * <ul>
 *   <li>serving the original through the API (access-checked and audited) instead of a public storage link, and
 *       refusing it if it no longer matches the fingerprint taken at upload;</li>
 *   <li>the version history (original + every stored signed version, with SHA-256 fingerprints);</li>
 *   <li>verifying a copy someone holds: is it the original, a stored signed version, or something altered?</li>
 * </ul>
 */
@Service
@Slf4j
public class DocumentIntegrityService {

    public record OriginalFile(byte[] bytes, String fileName, String contentType, String sha256) {}

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "xls", "application/vnd.ms-excel");

    private final EvidenceRepository evidenceRepo;
    private final EvidenceSignatureRepository signatureRepo;
    private final EvidenceDocumentVersionRepository versionRepo;
    private final CloudinaryService cloudinaryService;
    private final OrgAccessService orgAccessService;
    private final AuditTrailService auditTrail;

    public DocumentIntegrityService(EvidenceRepository evidenceRepo,
                                    EvidenceSignatureRepository signatureRepo,
                                    EvidenceDocumentVersionRepository versionRepo,
                                    CloudinaryService cloudinaryService,
                                    OrgAccessService orgAccessService,
                                    AuditTrailService auditTrail) {
        this.evidenceRepo = evidenceRepo;
        this.signatureRepo = signatureRepo;
        this.versionRepo = versionRepo;
        this.cloudinaryService = cloudinaryService;
        this.orgAccessService = orgAccessService;
        this.auditTrail = auditTrail;
    }

    private record Access(Evidence ev, User user) {}

    private Mono<Access> access(String email, UUID evidenceId) {
        return evidenceRepo.findById(evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Evidence not found")))
                .flatMap(ev -> orgAccessService.resolveGatedMember(ev.getOrganisationId(), email)
                        .map(ctx -> new Access(ev, ctx.user())));
    }

    // ── Original file ─────────────────────────────────────────────────────────────

    /** The original upload, checked against its fingerprint and recorded in the audit trail. */
    public Mono<OriginalFile> original(String email, UUID evidenceId, RequestMeta meta) {
        return access(email, evidenceId).flatMap(a -> fetchOriginal(a.ev())
                .flatMap(file -> auditTrail.record(a.ev(), AuditEventType.ORIGINAL_ACCESSED, a.user(), meta,
                                new AuditTrailService.Detail(0, file.sha256(), "Opened or downloaded the original file"))
                        .thenReturn(file)));
    }

    /**
     * Downloads the original and checks its SHA-256 against the one recorded at upload. Documents uploaded before
     * fingerprinting get theirs recorded now (from then on, any change to the stored file is detected).
     */
    Mono<OriginalFile> fetchOriginal(Evidence ev) {
        if (ev.getFileUpload() == null || ev.getFileUpload().isBlank()) {
            return Mono.error(new InvalidRecord("This evidence has no stored file"));
        }
        return cloudinaryService.downloadOriginal(ev.getFileUpload())
                .onErrorMap(e -> !(e instanceof InvalidRecord),
                        e -> new InvalidRecord("Could not fetch the original file: " + e.getMessage()))
                .flatMap(bytes -> {
                    String sha = SignedVersionService.sha256Hex(bytes);
                    OriginalFile file = new OriginalFile(bytes, ev.getDocumentName(), contentType(ev.getFileType()), sha);
                    if (ev.getOriginalSha256() == null) {
                        ev.setOriginalSha256(sha);
                        return evidenceRepo.save(ev).thenReturn(file);
                    }
                    if (!ev.getOriginalSha256().equals(sha)) {
                        log.error("Original file of evidence {} no longer matches the SHA-256 recorded at upload", ev.getId());
                        return Mono.error(new InvalidRecord(
                                "The stored original file failed its integrity check. Please contact support."));
                    }
                    return Mono.just(file);
                });
    }

    static String contentType(String fileType) {
        return CONTENT_TYPES.getOrDefault(fileType == null ? "" : fileType.toLowerCase(), "application/octet-stream");
    }

    // ── Version history ───────────────────────────────────────────────────────────

    public Mono<DocumentVersionsResponse> versions(String email, UUID evidenceId) {
        return access(email, evidenceId).flatMap(a -> versionRepo.findAllByEvidenceIdOrderByVersionNoAsc(evidenceId)
                .map(v -> new DocumentVersionsResponse.Version(v.getVersionNo(), v.getSha256(),
                        v.getSizeBytes() == null ? 0 : v.getSizeBytes(), v.getSignatureCount(), v.getCreatedAt()))
                .collectList()
                .map(list -> new DocumentVersionsResponse(new DocumentVersionsResponse.Original(
                        a.ev().getDocumentName(), a.ev().getFileType(), a.ev().getOriginalSha256(),
                        a.ev().getUploadedAt()), list)));
    }

    // ── Verify a copy ─────────────────────────────────────────────────────────────

    /** Checks whether {@code bytes} is exactly the original or one of the stored signed versions. */
    public Mono<VerificationResponse> verify(String email, UUID evidenceId, byte[] bytes, RequestMeta meta) {
        if (bytes == null || bytes.length == 0) {
            return Mono.error(new InvalidRecord("Choose a file to verify"));
        }
        String sha = SignedVersionService.sha256Hex(bytes);
        return access(email, evidenceId).flatMap(a -> versionRepo.findAllByEvidenceIdOrderByVersionNoAsc(evidenceId)
                .collectList()
                .flatMap(versions -> {
                    Integer latest = versions.isEmpty() ? null : versions.get(versions.size() - 1).getVersionNo();
                    EvidenceDocumentVersion match = versions.stream()
                            .filter(v -> sha.equals(v.getSha256())).findFirst().orElse(null);
                    if (match != null) {
                        return signersOf(evidenceId, match.getSignatureCount()).map(signers -> new VerificationResponse(
                                "SIGNED_VERSION", sha, match.getVersionNo(), latest,
                                match.getVersionNo().equals(latest), signers,
                                "This file is signed version v" + match.getVersionNo() + ", unchanged"
                                        + (match.getVersionNo().equals(latest) ? " — the latest version."
                                        : ". A newer signed version (v" + latest + ") exists.")));
                    }
                    return originalSha(a.ev()).map(originalSha -> sha.equals(originalSha)
                            ? new VerificationResponse("ORIGINAL", sha, 0, latest, versions.isEmpty(), List.of(),
                                    "This file is the original upload, unchanged and without signatures.")
                            : new VerificationResponse("NO_MATCH", sha, null, latest, false, List.of(),
                                    "This file does not match the original or any signed version. It may have been "
                                            + "changed after it was downloaded, or it is a different document."));
                })
                .flatMap(result -> auditTrail.record(a.ev(), AuditEventType.VERIFICATION, a.user(), meta,
                                new AuditTrailService.Detail(result.versionNo(), sha, "Verified a copy: " + result.result()))
                        .thenReturn(result)));
    }

    /** The original's recorded fingerprint, recording it first for older uploads that don't have one yet. */
    private Mono<String> originalSha(Evidence ev) {
        return ev.getOriginalSha256() != null
                ? Mono.just(ev.getOriginalSha256())
                : fetchOriginal(ev).map(OriginalFile::sha256).onErrorResume(e -> Mono.just(""));
    }

    /** A version with signature count k holds the first k signatures in signing order. */
    private Mono<List<VerificationResponse.Signer>> signersOf(UUID evidenceId, int count) {
        return signatureRepo.findAllByEvidenceIdOrderBySignedAtAsc(evidenceId)
                .take(count)
                .map(s -> new VerificationResponse.Signer(s.getSignerName(), s.getSignerTitle(), s.getSignedAt()))
                .collectList();
    }
}
