package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.*;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.*;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import com.diana.auditinsightbackendspringboot.Services.OrgAccessService.OrgMemberContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository txnRepo;
    private final EvidenceRepository evidenceRepo;
    private final ReviewQueueRepository reviewRepo;
    private final OrganisationRepository organisationRepo;
    private final OrgAccessService orgAccessService;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final EvidenceSignatureRepository signatureRepo;
    private final SigningModeService signingMode;

    public TransactionService(TransactionRepository txnRepo,
                              EvidenceRepository evidenceRepo,
                              ReviewQueueRepository reviewRepo,
                              OrganisationRepository organisationRepo,
                              OrgAccessService orgAccessService,
                              UserRepository userRepo,
                              NotificationService notificationService,
                              EvidenceSignatureRepository signatureRepo,
                              SigningModeService signingMode) {
        this.txnRepo = txnRepo;
        this.evidenceRepo = evidenceRepo;
        this.reviewRepo = reviewRepo;
        this.organisationRepo = organisationRepo;
        this.orgAccessService = orgAccessService;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
        this.signatureRepo = signatureRepo;
        this.signingMode = signingMode;
    }


    private Mono<OrgMemberContext> resolveContext(UUID orgId, String email) {
        return orgAccessService.resolveGatedMember(orgId, email);
    }


    private TransactionResponse toResponse(Transaction t, String creatorName) {
        TransactionResponse r = new TransactionResponse();
        r.setId(t.getId
                ());
        r.setOrganisationId(t.getOrganisationId());
        r.setName(t.getName());
        r.setDate(t.getDate());
        r.setCounterparty(t.getCounterparty());
        r.setDonor(t.getDonor());
        r.setBudgetLine(t.getBudgetLine());
        r.setAmount(t.getAmount());
        r.setType(t.getType());
        r.setPaymentMethod(t.getPaymentMethod());
        r.setStatus(t.getStatus());
        r.setEvidenceStatus(t.getEvidenceStatus());
        r.setCreatedBy(creatorName);
        r.setCreatedAt(t.getCreatedAt());
        return r;
    }

    private Mono<String> resolveCreatorName(Long userId) {
        return userRepo.findById(userId)
                .map(User::getFullName)
                .defaultIfEmpty("Unknown");
    }


    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }


    public Mono<TransactionResponse> createTransaction(String email, CreateTransactionRequest req) {
        return Mono.zip(
                        resolveContext(req.getOrganisationId(), email),
                        organisationRepo.findById(req.getOrganisationId())
                                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found"))))
                .flatMap(tuple -> {
                    OrgMemberContext ctx = tuple.getT1();
                    Organisation org = tuple.getT2();

                    if (!orgAccessService.hasPermission(ctx.role(), Permission.TRANSACTION_WRITE)) {
                        return Mono.error(new ForbiddenException(
                                "Permission denied. Auditors cannot create transactions."));
                    }

                    if (org.getIndustry() == OrganisationType.NGO
                            && (isBlank(req.getDonor()) || isBlank(req.getBudgetLine()))) {
                        return Mono.error(new InvalidRecord(
                                "Donor and budget line are required for NGO transactions."));
                    }

                    Transaction t = new Transaction();
                    t.setId(UUID.randomUUID());
                    t.setOrganisationId(req.getOrganisationId());
                    t.setName(req.getName());
                    t.setDate(req.getDate());
                    t.setAmount(req.getAmount());
                    t.setType(req.getType());
                    t.setCounterparty(req.getCounterparty());
                    t.setDonor(req.getDonor());
                    t.setBudgetLine(req.getBudgetLine());
                    t.setPaymentMethod(req.getPaymentMethod());
                    t.setStatus(TransactionStatus.PENDING);
                    t.setEvidenceStatus(EvidenceStatus.MISSING);
                    t.setCreatedBy(ctx.user().getId());
                    t.setCreatedAt(LocalDateTime.now());
                    t.setNewRecord(true);

                    return txnRepo.save(t)
                            .flatMap(saved -> notificationService
                                    .notifyTransactionCreated(
                                            saved.getOrganisationId(),
                                            saved.getId(),
                                            saved.getName(),
                                            ctx.user().getFullName())
                                    .thenReturn(toResponse(saved, ctx.user().getFullName())));
                });
    }


    public Flux<TransactionResponse> listTransactions(UUID orgId, String email) {
        return resolveContext(orgId, email)
                .thenMany(txnRepo.findAllByOrganisationId(orgId))
                .flatMap(t -> resolveCreatorName(t.getCreatedBy()).map(name -> toResponse(t, name)));
    }


    public Mono<TransactionResponse> getTransaction(UUID txnId, String email) {
        return txnRepo.findById(txnId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                .flatMap(t -> resolveContext(t.getOrganisationId(), email)
                        .then(resolveCreatorName(t.getCreatedBy()))
                        .flatMap(creatorName -> signatureRepo.findSignedEvidenceIdsByTransactionId(txnId)
                                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                                .flatMap(signatureCounts -> evidenceRepo.findAllByTransactionId(txnId)
                                .map(e -> {
                                    EvidenceResponse er = new EvidenceResponse();
                                    er.setId(e.getId());
                                    er.setOrganisationId(e.getOrganisationId());
                                    er.setTransactionId(e.getTransactionId());
                                    er.setDocumentName(e.getDocumentName());
                                    er.setFolder(e.getFolder());
                                    er.setSubfolder(e.getSubfolder());
                                    er.setFileUpload(e.getFileUpload());
                                    er.setFileType(e.getFileType());
                                    er.setNotes(e.getNotes());
                                    er.setUploadedBy(e.getUploadedBy());
                                    er.setUploadedAt(e.getUploadedAt());
                                    er.setSignatureCount(signatureCounts.getOrDefault(e.getId(), 0L).intValue());
                                    er.setPlaceable(signingMode.isPlaceable(e));
                                    return er;
                                })
                                .collectList()
                                .map(evidenceList -> {
                                    TransactionResponse r = toResponse(t, creatorName);
                                    r.setEvidence(evidenceList);
                                    return r;
                                }))));
    }


    public Mono<TransactionResponse> updateStatus(UUID txnId, String email,
                                                  UpdateTransactionStatusRequest req) {
        return txnRepo.findById(txnId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                .flatMap(t -> resolveContext(t.getOrganisationId(), email)
                        .flatMap(ctx -> {
                            if (!orgAccessService.hasPermission(ctx.role(), Permission.TRANSACTION_WRITE)) {
                                return Mono.error(new ForbiddenException(
                                        "Permission denied. Auditors cannot update transaction status."));
                            }
                            t.setStatus(req.getStatus());

                            // Auto-flag: COMPLETED + MISSING evidence → system flag
                            final Mono<Void> autoFlag =
                                    (req.getStatus() == TransactionStatus.COMPLETED
                                            && t.getEvidenceStatus() == EvidenceStatus.MISSING)
                                            ? createSystemFlag(t.getOrganisationId(), txnId)
                                            : Mono.empty();

                            return txnRepo.save(t)
                                    .flatMap(saved -> autoFlag
                                            .then(resolveCreatorName(saved.getCreatedBy()))
                                            .map(name -> toResponse(saved, name)));
                        }));
    }


    Mono<Void> createSystemFlag(UUID orgId, UUID txnId) {
        return reviewRepo.existsByTransactionIdAndFlaggedByAndStatus(txnId, "system", ReviewStatus.OPEN)
                .flatMap(exists -> {
                    if (exists) return Mono.empty();
                    ReviewQueue flag = new ReviewQueue();
                    flag.setOrganisationId(orgId);
                    flag.setTransactionId(txnId);
                    flag.setIssueType(IssueType.MISSING_EVIDENCE);
                    flag.setDescription("Transaction " + txnId + " has no supporting evidence linked.");
                    flag.setStatus(ReviewStatus.OPEN);
                    flag.setFlaggedBy("system");
                    flag.setCreatedAt(LocalDateTime.now());
                    return reviewRepo.save(flag).then();
                });
    }


    Mono<Void> recalculateEvidenceStatus(UUID txnId) {
        return evidenceRepo.countByTransactionId(txnId)
                .flatMap(count -> txnRepo.findById(txnId)
                        .flatMap(t -> {
                            EvidenceStatus newStatus;
                            if (count == 0) {
                                newStatus = EvidenceStatus.MISSING;
                            } else if (count < 3) {
                                newStatus = EvidenceStatus.PARTIAL;
                            } else {
                                newStatus = EvidenceStatus.COMPLETE;
                            }

                            if (newStatus == t.getEvidenceStatus()) return Mono.empty();

                            t.setEvidenceStatus(newStatus);
                            return txnRepo.save(t).flatMap(saved -> {
                                if (newStatus == EvidenceStatus.COMPLETE) {

                                    return reviewRepo.findByTransactionIdAndStatus(txnId, ReviewStatus.OPEN)
                                            .filter(rq -> rq.getIssueType() == IssueType.MISSING_EVIDENCE)
                                            .flatMap(rq -> {
                                                rq.setStatus(ReviewStatus.RESOLVED);
                                                rq.setFlaggedBy("system");
                                                rq.setResolutionNote("Evidence status reached COMPLETE — auto-resolved.");
                                                rq.setResolvedAt(LocalDateTime.now());
                                                return reviewRepo.save(rq);
                                            })
                                            .then();
                                }
                                return Mono.empty();
                            });
                        }));
    }
}
