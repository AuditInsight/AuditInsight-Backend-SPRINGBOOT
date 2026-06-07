package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.CreateReviewQueueRequest;
import com.diana.auditinsightbackendspringboot.DTOs.ResolveReviewQueueRequest;
import com.diana.auditinsightbackendspringboot.DTOs.ReviewQueueResponse;
import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.ReviewStatus;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.ReviewQueue;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ReviewQueueService {

    private final ReviewQueueRepository reviewRepo;
    private final TransactionRepository txnRepo;
    private final OrganisationMemberRepository memberRepo;
    private final UserRepository userRepo;

    public ReviewQueueService(ReviewQueueRepository reviewRepo,
                              TransactionRepository txnRepo,
                              OrganisationMemberRepository memberRepo,
                              UserRepository userRepo) {
        this.reviewRepo = reviewRepo;
        this.txnRepo = txnRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
    }


    private record OrgContext(User user, Role role) {}

    private Mono<OrgContext> resolveContext(UUID orgId, String email) {
        return userRepo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMap(user -> memberRepo.findByOrganisationIdAndUserId(orgId, user.getId())
                        .switchIfEmpty(Mono.error(new ForbiddenException(
                                "You are not a member of this organisation")))
                        .flatMap(m -> m.getStatus() != MemberStatus.ACTIVE
                                ? Mono.error(new ForbiddenException("Your membership is not active"))
                                : Mono.just(new OrgContext(user, m.getRole()))));
    }


    private ReviewQueueResponse toResponse(ReviewQueue rq) {
        ReviewQueueResponse r = new ReviewQueueResponse();
        r.setId(rq.getId());
        r.setOrganisationId(rq.getOrganisationId());
        r.setTransactionId(rq.getTransactionId());
        r.setIssueType(rq.getIssueType());
        r.setDescription(rq.getDescription());
        r.setStatus(rq.getStatus());
        r.setFlaggedBy(rq.getFlaggedBy());
        r.setResolvedBy(rq.getResolvedBy());
        r.setResolutionNote(rq.getResolutionNote());
        r.setCreatedAt(rq.getCreatedAt());
        r.setResolvedAt(rq.getResolvedAt());
        return r;
    }


    public Mono<ReviewQueueResponse> flagIssue(String email, CreateReviewQueueRequest req) {
        return resolveContext(req.getOrganisationId(), email)
                .flatMap(ctx -> {
                    if (ctx.role() != Role.AUDITOR) {
                        return Mono.error(new ForbiddenException(
                                "Permission denied. Only auditors can flag issues."));
                    }
                    return txnRepo.findById(req.getTransactionId())
                            .switchIfEmpty(Mono.error(new InvalidRecord("Transaction not found")))
                            .flatMap(txn -> {
                                if (!txn.getOrganisationId().equals(req.getOrganisationId())) {
                                    return Mono.error(new InvalidRecord(
                                            "Transaction does not belong to this organisation"));
                                }
                                ReviewQueue rq = new ReviewQueue();
                                rq.setOrganisationId(req.getOrganisationId());
                                rq.setTransactionId(req.getTransactionId());
                                rq.setIssueType(req.getIssueType());
                                rq.setDescription(req.getDescription());
                                rq.setStatus(ReviewStatus.OPEN);
                                rq.setFlaggedBy(String.valueOf(ctx.user().getId()));
                                rq.setCreatedAt(LocalDateTime.now());
                                return reviewRepo.save(rq).map(this::toResponse);
                            });
                });
    }


    public Flux<ReviewQueueResponse> listByOrg(UUID orgId, String email) {
        return resolveContext(orgId, email)
                .thenMany(reviewRepo.findAllByOrganisationId(orgId))
                .map(this::toResponse);
    }


    public Mono<ReviewQueueResponse> getItem(UUID itemId, String email) {
        return reviewRepo.findById(itemId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Review queue item not found")))
                .flatMap(rq -> resolveContext(rq.getOrganisationId(), email)
                        .thenReturn(toResponse(rq)));
    }

    public Mono<ReviewQueueResponse> resolveIssue(UUID itemId, String email,
                                                  ResolveReviewQueueRequest req) {
        return reviewRepo.findById(itemId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Review queue item not found")))
                .flatMap(rq -> resolveContext(rq.getOrganisationId(), email)
                        .flatMap(ctx -> {
                            if (ctx.role() == Role.AUDITOR) {
                                return Mono.error(new ForbiddenException(
                                        "Permission denied. Auditors cannot resolve issues."));
                            }
                            if (rq.getStatus() == ReviewStatus.RESOLVED) {
                                return Mono.error(new InvalidRecord(
                                        "This issue has already been resolved."));
                            }
                            rq.setStatus(ReviewStatus.RESOLVED);
                            rq.setResolvedBy(ctx.user().getId());
                            rq.setResolutionNote(req.getResolutionNote());
                            rq.setResolvedAt(LocalDateTime.now());
                            return reviewRepo.save(rq).map(this::toResponse);
                        }));
    }
}
