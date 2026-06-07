package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.EvidenceResponse;
import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.Role;
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

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class EvidenceService {

    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "xls", "xlsx", "jpeg", "jpg", "png");

    private final EvidenceRepository evidenceRepo;
    private final TransactionRepository txnRepo;
    private final OrganisationMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final TransactionService txnService;
    private final CloudinaryService cloudinaryService;
    private final NotificationService notificationService;

    public EvidenceService(EvidenceRepository evidenceRepo,
                           TransactionRepository txnRepo,
                           OrganisationMemberRepository memberRepo,
                           UserRepository userRepo,
                           TransactionService txnService,
                           CloudinaryService cloudinaryService,
                           NotificationService notificationService) {
        this.evidenceRepo = evidenceRepo;
        this.txnRepo = txnRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.txnService = txnService;
        this.cloudinaryService = cloudinaryService;
        this.notificationService = notificationService;
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
                    "File type '" + ext + "' is not allowed. Accepted types: pdf, xls, xlsx, jpeg, jpg, png"));
        }
        return Mono.just(ext);
    }



    private Mono<User> assertCanUpload(UUID orgId, String email) {
        return userRepo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMap(user -> memberRepo.findByOrganisationIdAndUserId(orgId, user.getId())
                        .switchIfEmpty(Mono.error(new ForbiddenException(
                                "You are not a member of this organisation")))
                        .flatMap(m -> {
                            if (m.getStatus() != MemberStatus.ACTIVE) {
                                return Mono.error(new ForbiddenException("Your membership is not active"));
                            }
                            if (m.getRole() == Role.AUDITOR) {
                                return Mono.error(new ForbiddenException(
                                        "Permission denied. Auditors cannot upload evidence."));
                            }
                            return Mono.just(user);
                        }));
    }

    private Mono<User> assertCanView(UUID orgId, String email) {
        return userRepo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMap(user -> memberRepo.findByOrganisationIdAndUserId(orgId, user.getId())
                        .switchIfEmpty(Mono.error(new ForbiddenException(
                                "You are not a member of this organisation")))
                        .flatMap(m -> m.getStatus() != MemberStatus.ACTIVE
                                ? Mono.error(new ForbiddenException("Your membership is not active"))
                                : Mono.just(user)));
    }



    private EvidenceResponse toResponse(Evidence e) {
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
        return r;
    }



    public Mono<EvidenceResponse> uploadEvidence(String email, FilePart filePart,
                                                 UUID organisationId, String transactionId,
                                                 String documentName, String folder,
                                                 String subfolder, String notes) {
        if (!EvidenceFolderValidator.isValid(folder, subfolder)) {
            return Mono.error(new InvalidRecord(
                    "Invalid folder or subfolder. Check the allowed evidence folder structure."));
        }

        return detectFileType(filePart)
                .flatMap(fileType ->
                        assertCanUpload(organisationId, email)
                                .flatMap(user ->
                                        txnRepo.findById(transactionId)
                                                .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                                                .flatMap(txn -> {
                                                    if (!txn.getOrganisationId().equals(organisationId)) {
                                                        return Mono.error(new InvalidRecord(
                                                                "Transaction does not belong to this organisation"));
                                                    }
                                                    // Read file bytes, upload to Cloudinary, save evidence
                                                    return DataBufferUtils.join(filePart.content())
                                                            .map(buf -> {
                                                                byte[] bytes = new byte[buf.readableByteCount()];
                                                                buf.read(bytes);
                                                                DataBufferUtils.release(buf);
                                                                return bytes;
                                                            })
                                                            .flatMap(bytes ->
                                                                    cloudinaryService.upload(bytes, organisationId.toString()))
                                                            .flatMap(url -> {
                                                                Evidence ev = new Evidence();
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
                                                                return evidenceRepo.save(ev);
                                                            })
                                                            .flatMap(saved ->
                                                                    txnService.recalculateEvidenceStatus(transactionId)
                                                                            .then(notificationService.notifyEvidenceUploaded(
                                                                                    organisationId,
                                                                                    transactionId,
                                                                                    documentName,
                                                                                    user.getFullName()))
                                                                            .thenReturn(toResponse(saved)));
                                                })
                                )
                );
    }



    public Flux<EvidenceResponse> listByOrg(UUID orgId, String email) {
        return assertCanView(orgId, email)
                .thenMany(evidenceRepo.findAllByOrganisationId(orgId))
                .map(this::toResponse);
    }


    public Mono<EvidenceResponse> getEvidence(UUID evidenceId, String email) {
        return evidenceRepo.findById(evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Evidence not found")))
                .flatMap(ev -> assertCanView(ev.getOrganisationId(), email)
                        .thenReturn(toResponse(ev)));
    }


    public Flux<EvidenceResponse> listByTransaction(String txnId, String email) {
        return txnRepo.findById(txnId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                .flatMapMany(txn -> assertCanView(txn.getOrganisationId(), email)
                        .thenMany(evidenceRepo.findAllByTransactionId(txnId))
                        .map(this::toResponse));
    }
}