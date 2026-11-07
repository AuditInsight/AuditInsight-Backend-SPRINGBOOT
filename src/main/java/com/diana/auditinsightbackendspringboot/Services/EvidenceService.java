package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.EvidenceResponse;
import com.diana.auditinsightbackendspringboot.DTOs.RequestMeta;
import com.diana.auditinsightbackendspringboot.Enum.AuditEventType;
import com.diana.auditinsightbackendspringboot.Enum.Permission;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EvidenceService {

    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "xls", "xlsx", "jpeg", "jpg", "png", "docx");

    private final EvidenceRepository evidenceRepo;
    private final TransactionRepository txnRepo;
    private final OrganisationRepository organisationRepo;
    private final OrgAccessService orgAccessService;
    private final TransactionService txnService;
    private final CloudinaryService cloudinaryService;
    private final NotificationService notificationService;
    private final EvidenceSignatureRepository signatureRepo;
    private final AuditTrailService auditTrail;
    private final SigningModeService signingMode;

    public EvidenceService(EvidenceRepository evidenceRepo,
                           TransactionRepository txnRepo,
                           OrganisationRepository organisationRepo,
                           OrgAccessService orgAccessService,
                           TransactionService txnService,
                           CloudinaryService cloudinaryService,
                           NotificationService notificationService,
                           EvidenceSignatureRepository signatureRepo,
                           AuditTrailService auditTrail,
                           SigningModeService signingMode) {
        this.evidenceRepo = evidenceRepo;
        this.txnRepo = txnRepo;
        this.organisationRepo = organisationRepo;
        this.orgAccessService = orgAccessService;
        this.txnService = txnService;
        this.cloudinaryService = cloudinaryService;
        this.notificationService = notificationService;
        this.signatureRepo = signatureRepo;
        this.auditTrail = auditTrail;
        this.signingMode = signingMode;
    }



    private Mono<String> detectFileType(FilePart filePart) {
        String filename = filePart.filename();
        if (filename == null || !filename.contains(".")) {
            return Mono.error(new InvalidRecord(
                    "Cannot determine file type — file must have an extension"));
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            return Mono.error(new InvalidRecord(
                    "File type '" + ext + "' is not allowed. Accepted types: pdf, xls, xlsx, jpeg, jpg, png, docx"));
        }
        return Mono.just(ext);
    }



    private Mono<User> assertCanUpload(UUID orgId, String email) {
        return orgAccessService.resolveGatedMember(orgId, email)
                .flatMap(ctx -> {
                    if (!orgAccessService.hasPermission(ctx.role(), Permission.EVIDENCE_UPLOAD)) {
                        return Mono.error(new ForbiddenException(
                                "Permission denied. Auditors cannot upload evidence."));
                    }
                    return Mono.just(ctx.user());
                });
    }

    private Mono<User> assertCanView(UUID orgId, String email) {
        return orgAccessService.resolveGatedMember(orgId, email)
                .map(ctx -> ctx.user());
    }



    private EvidenceResponse toResponse(Evidence e) {
        return toResponse(e, 0);
    }

    private EvidenceResponse toResponse(Evidence e, long signatureCount) {
        EvidenceResponse r = new EvidenceResponse();
        r.setId(e.getId());
        r.setOrganisationId(e.getOrganisationId());
        r.setTransactionId(e.getTransactionId());
        r.setDocumentName(e.getDocumentName());
        r.setFolder(e.getFolder());
        r.setSubfolder(e.getSubfolder());
        r.setFileUpload(e.getFileUpload());
        r.setFileType(e.getFileType());
        r.setNotes(e.getNotes());
        r.setUploadedBy(e.getUploadedBy());
        r.setUploadedAt(e.getUploadedAt());
        r.setSignatureCount((int) signatureCount);
        r.setPlaceable(signingMode.isPlaceable(e));
        return r;
    }

    /** Signatures per evidence id, built from one row per signature. */
    private static Mono<Map<UUID, Long>> countPerEvidence(Flux<UUID> signedEvidenceIds) {
        return signedEvidenceIds.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }



    public Mono<EvidenceResponse> uploadEvidence(String email, FilePart filePart,
                                                 UUID organisationId, UUID transactionId,
                                                 String documentName, String folder,
                                                 String subfolder, String notes) {
        return organisationRepo.findById(organisationId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")))
                .flatMap(org -> {
                    if (!EvidenceFolderValidator.isValid(org.getIndustry(), folder, subfolder)) {
                        return Mono.error(new InvalidRecord(
                                "Invalid folder or subfolder. Check the allowed evidence folder structure."));
                    }
                    return detectFileType(filePart);
                })
                .flatMap(fileType ->
                        assertCanUpload(organisationId, email)
                                .flatMap(user -> {
                                    if (transactionId == null) {
                                        // Standalone document — not linked to any transaction (e.g. uploaded
                                        // purely to be signed), so there's no transaction status to recalculate.
                                        return saveEvidenceFile(filePart, organisationId, null,
                                                documentName, folder, subfolder, notes, fileType, user)
                                                .map(this::toResponse);
                                    }
                                    return txnRepo.findById(transactionId)
                                            .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                                            .flatMap(txn -> {
                                                if (!txn.getOrganisationId().equals(organisationId)) {
                                                    return Mono.error(new InvalidRecord(
                                                            "Transaction does not belong to this organisation"));
                                                }
                                                return saveEvidenceFile(filePart, organisationId, transactionId,
                                                        documentName, folder, subfolder, notes, fileType, user)
                                                        .flatMap(saved ->
                                                                txnService.recalculateEvidenceStatus(transactionId)
                                                                        .then(notificationService.notifyEvidenceUploaded(
                                                                                organisationId,
                                                                                transactionId,
                                                                                documentName,
                                                                                user.getFullName()))
                                                                        .thenReturn(toResponse(saved)));
                                            });
                                })
                );
    }

    private Mono<Evidence> saveEvidenceFile(FilePart filePart, UUID organisationId, UUID transactionId,
                                            String documentName, String folder, String subfolder,
                                            String notes, String fileType, User user) {
        // Read file bytes, upload to Cloudinary, save evidence
        return DataBufferUtils.join(filePart.content())
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return bytes;
                })
                .flatMap(bytes -> {
                    // Fingerprint the original so any later change to the stored file can be detected.
                    String sha256 = SignedVersionService.sha256Hex(bytes);
                    return cloudinaryService.upload(bytes, organisationId.toString(), fileType)
                            .map(url -> Tuples.of(url, sha256));
                })
                .flatMap(stored -> {
                    String url = stored.getT1();
                    Evidence ev = new Evidence();
                    ev.setOriginalSha256(stored.getT2());
                    ev.setOrganisationId(organisationId);
                    ev.setTransactionId(transactionId);
                    ev.setDocumentName(documentName);
                    ev.setFolder(folder);
                    ev.setSubfolder(subfolder);
                    ev.setFileUpload(url);
                    ev.setFileType(fileType);
                    ev.setNotes(notes);
                    ev.setUploadedBy(user.getId());
                    ev.setUploadedAt(LocalDateTime.now());
                    return evidenceRepo.save(ev).flatMap(saved -> auditTrail.record(saved,
                                    AuditEventType.DOCUMENT_UPLOADED, user, RequestMeta.NONE,
                                    new AuditTrailService.Detail(0, saved.getOriginalSha256(),
                                            "Uploaded " + filePart.filename()))
                            .thenReturn(saved));
                });
    }



    public Flux<EvidenceResponse> listByOrg(UUID orgId, String email) {
        return assertCanView(orgId, email)
                .then(Mono.defer(() -> countPerEvidence(signatureRepo.findSignedEvidenceIdsByOrganisationId(orgId))))
                .flatMapMany(counts -> evidenceRepo.findAllByOrganisationId(orgId)
                        .map(e -> toResponse(e, counts.getOrDefault(e.getId(), 0L))));
    }


    public Mono<EvidenceResponse> getEvidence(UUID evidenceId, String email) {
        return evidenceRepo.findById(evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Evidence not found")))
                .flatMap(ev -> assertCanView(ev.getOrganisationId(), email)
                        .then(Mono.defer(() -> signatureRepo.countByEvidenceId(evidenceId)))
                        .defaultIfEmpty(0L)
                        .map(count -> toResponse(ev, count)));
    }


    public Flux<EvidenceResponse> listByTransaction(UUID txnId, String email) {
        return txnRepo.findById(txnId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                .flatMapMany(txn -> assertCanView(txn.getOrganisationId(), email)
                        .then(Mono.defer(() -> countPerEvidence(signatureRepo.findSignedEvidenceIdsByTransactionId(txnId))))
                        .flatMapMany(counts -> evidenceRepo.findAllByTransactionId(txnId)
                                .map(e -> toResponse(e, counts.getOrDefault(e.getId(), 0L)))));
    }
}