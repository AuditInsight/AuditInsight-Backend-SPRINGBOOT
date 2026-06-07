package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.*;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.*;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository txnRepo;
    private final EvidenceRepository evidenceRepo;
    private final ReviewQueueRepository reviewRepo;
    private final OrganisationMemberRepository memberRepo;
    private final UserRepository userRepo;

    public TransactionService(TransactionRepository txnRepo,
                              EvidenceRepository evidenceRepo,
                              ReviewQueueRepository reviewRepo,
                              OrganisationMemberRepository memberRepo,
                              UserRepository userRepo) {
        this.txnRepo = txnRepo;
        this.evidenceRepo = evidenceRepo;
        this.reviewRepo = reviewRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
    }


    private Mono<OrgMemberContext> resolveContext(UUID orgId, String email) {
        return userRepo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMap(user -> memberRepo.findByOrganisationIdAndUserId(orgId, user.getId())
                        .switchIfEmpty(Mono.error(new ForbiddenException(
                                "You are not a member of this organisation")))
                        .flatMap(m -> m.getStatus() != MemberStatus.ACTIVE
                                ? Mono.error(new ForbiddenException("Your membership is not active"))
                                : Mono.just(new OrgMemberContext(user, m.getRole()))));
    }

    private record OrgMemberContext(User user, Role role) {}


    private Mono<String> generateTxnId(UUID orgId) {
        return txnRepo.countByOrganisationId(orgId)
                .map(count -> String.format("TXN-%04d", count + 1));
    }


    private TransactionResponse toResponse(Transaction t, String creatorName) {
        TransactionResponse r = new TransactionResponse();
        r.setId(t.getId());
        r.setOrganisationId(t.getOrganisationId());
        r.setName(t.getName());
        r.setDate(t.getDate());
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


    public Mono<TransactionResponse> createTransaction(String email, CreateTransactionRequest req) {
        return resolveContext(req.getOrganisationId(), email)
                .flatMap(ctx -> {
                    if (ctx.role() == Role.AUDITOR) {
                        return Mono.error(new ForbiddenException(
                                "Permission denied. Auditors cannot create transactions."));
                    }
                    return generateTxnId(req.getOrganisationId())
                            .flatMap(txnId -> {
                                Transaction t = new Transaction();
                                t.setId(txnId);
                                t.setOrganisationId(req.getOrganisationId());
                                t.setName(req.getName());
                                t.setDate(req.getDate());
                                t.setAmount(req.getAmount());
                                t.setType(req.getType());
                                t.setPaymentMethod(req.getPaymentMethod());
                                t.setStatus(TransactionStatus.PENDING);
                                t.setEvidenceStatus(EvidenceStatus.MISSING);
                                t.setCreatedBy(ctx.user().getId());
                                t.setCreatedAt(LocalDateTime.now());
                                t.setNewRecord(true);

                                return txnRepo.save(t).map(saved -> toResponse(saved, ctx.user().getFullName()));
                            });
                });
    }


    public Flux<TransactionResponse> listTransactions(UUID orgId, String email) {
        return resolveContext(orgId, email)
                .thenMany(txnRepo.findAllByOrganisationId(orgId))
                .flatMap(t -> resolveCreatorName(t.getCreatedBy()).map(name -> toResponse(t, name)));
    }


    public Mono<TransactionResponse> getTransaction(String txnId, String email) {
        return txnRepo.findById(txnId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                .flatMap(t -> resolveContext(t.getOrganisationId(), email)
                        .then(resolveCreatorName(t.getCreatedBy()))
                        .flatMap(creatorName -> evidenceRepo.findAllByTransactionId(txnId)
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
                                    return er;
                                })
                                .collectList()
                                .map(evidenceList -> {
                                    TransactionResponse r = toResponse(t, creatorName);
                                    r.setEvidence(evidenceList);
                                    return r;
                                })));
    }


    public Mono<TransactionResponse> updateStatus(String txnId, String email,
                                                  UpdateTransactionStatusRequest req) {
        return txnRepo.findById(txnId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                .flatMap(t -> resolveContext(t.getOrganisationId(), email)
                        .flatMap(ctx -> {
                            if (ctx.role() == Role.AUDITOR) {
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


    Mono<Void> createSystemFlag(UUID orgId, String txnId) {
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


    Mono<Void> recalculateEvidenceStatus(String txnId) {
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
