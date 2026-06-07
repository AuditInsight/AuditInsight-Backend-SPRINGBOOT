package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.CreateReviewQueueRequest;
import com.diana.auditinsightbackendspringboot.DTOs.ResolveReviewQueueRequest;
import com.diana.auditinsightbackendspringboot.Enum.*;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.*;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTest {

    @Mock private ReviewQueueRepository reviewRepo;
    @Mock private TransactionRepository txnRepo;
    @Mock private OrganisationMemberRepository memberRepo;
    @Mock private UserRepository userRepo;

    private ReviewQueueService service;

    private final UUID ORG_ID  = UUID.randomUUID();
    private final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReviewQueueService(reviewRepo, txnRepo, memberRepo, userRepo);
    }

    // ──────────────────────────── flagIssue ──────────────────────────────────

    @Test
    void flagIssue_auditorUser_succeeds() {
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);
        Transaction t = txn("TXN-0001");
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));
        when(reviewRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.flagIssue("auditor@test.com", flagRequest()))
                .expectNextMatches(r -> r.getIssueType() == IssueType.COMPLIANCE_ISSUE
                        && r.getStatus() == ReviewStatus.OPEN
                        && r.getFlaggedBy().equals("3"))
                .verifyComplete();
    }

    @Test
    void flagIssue_clientUser_returnsForbidden() {
        mockActiveMember("client@test.com", 1L, Role.CLIENT);

        StepVerifier.create(service.flagIssue("client@test.com", flagRequest()))
                .expectErrorMatches(e -> e instanceof ForbiddenException
                        && e.getMessage().contains("Only auditors can flag issues"))
                .verify();
    }

    @Test
    void flagIssue_memberUser_returnsForbidden() {
        mockActiveMember("member@test.com", 2L, Role.MEMBER);

        StepVerifier.create(service.flagIssue("member@test.com", flagRequest()))
                .expectErrorMatches(e -> e instanceof ForbiddenException
                        && e.getMessage().contains("Only auditors can flag issues"))
                .verify();
    }

    @Test
    void flagIssue_transactionNotFound_returnsError() {
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.empty());

        StepVerifier.create(service.flagIssue("auditor@test.com", flagRequest()))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Transaction not found"))
                .verify();
    }

    @Test
    void flagIssue_transactionFromDifferentOrg_returnsError() {
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);
        Transaction t = txn("TXN-0001");
        t.setOrganisationId(UUID.randomUUID()); // different org
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));

        StepVerifier.create(service.flagIssue("auditor@test.com", flagRequest()))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("does not belong to this organisation"))
                .verify();
    }

    @Test
    void flagIssue_nonMember_returnsForbidden() {
        User user = user(9L, "stranger@test.com", Role.AUDITOR);
        when(userRepo.findByUsername("stranger@test.com")).thenReturn(Mono.just(user));
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.flagIssue("stranger@test.com", flagRequest()))
                .expectErrorMatches(e -> e instanceof ForbiddenException
                        && e.getMessage().contains("not a member"))
                .verify();
    }

    // ──────────────────────────── listByOrg ──────────────────────────────────

    @Test
    void listByOrg_anyActiveMember_returnsItems() {
        mockActiveMember("member@test.com", 2L, Role.MEMBER);
        ReviewQueue rq1 = reviewQueueItem("TXN-0001", ReviewStatus.OPEN);
        ReviewQueue rq2 = reviewQueueItem("TXN-0002", ReviewStatus.RESOLVED);
        when(reviewRepo.findAllByOrganisationId(ORG_ID)).thenReturn(Flux.just(rq1, rq2));

        StepVerifier.create(service.listByOrg(ORG_ID, "member@test.com"))
                .expectNextCount(2)
                .verifyComplete();
    }

    // ──────────────────────────── getItem ────────────────────────────────────

    @Test
    void getItem_activeMember_returnsItem() {
        ReviewQueue rq = reviewQueueItem("TXN-0001", ReviewStatus.OPEN);
        rq.setId(ITEM_ID);
        when(reviewRepo.findById(ITEM_ID)).thenReturn(Mono.just(rq));
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);

        StepVerifier.create(service.getItem(ITEM_ID, "auditor@test.com"))
                .expectNextMatches(r -> r.getId().equals(ITEM_ID)
                        && r.getStatus() == ReviewStatus.OPEN)
                .verifyComplete();
    }

    @Test
    void getItem_notFound_returnsError() {
        when(reviewRepo.findById(ITEM_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getItem(ITEM_ID, "client@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Review queue item not found"))
                .verify();
    }

    // ──────────────────────────── resolveIssue ───────────────────────────────

    @Test
    void resolveIssue_clientUser_succeeds() {
        ReviewQueue rq = reviewQueueItem("TXN-0001", ReviewStatus.OPEN);
        rq.setId(ITEM_ID);
        when(reviewRepo.findById(ITEM_ID)).thenReturn(Mono.just(rq));
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(reviewRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ResolveReviewQueueRequest req = new ResolveReviewQueueRequest();
        req.setResolutionNote("Evidence uploaded and verified.");

        StepVerifier.create(service.resolveIssue(ITEM_ID, "client@test.com", req))
                .expectNextMatches(r -> r.getStatus() == ReviewStatus.RESOLVED
                        && r.getResolutionNote().equals("Evidence uploaded and verified.")
                        && r.getResolvedBy().equals(1L))
                .verifyComplete();
    }

    @Test
    void resolveIssue_memberUser_succeeds() {
        ReviewQueue rq = reviewQueueItem("TXN-0001", ReviewStatus.OPEN);
        rq.setId(ITEM_ID);
        when(reviewRepo.findById(ITEM_ID)).thenReturn(Mono.just(rq));
        mockActiveMember("member@test.com", 2L, Role.MEMBER);
        when(reviewRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ResolveReviewQueueRequest req = new ResolveReviewQueueRequest();
        req.setResolutionNote("Reconciliation completed.");

        StepVerifier.create(service.resolveIssue(ITEM_ID, "member@test.com", req))
                .expectNextMatches(r -> r.getStatus() == ReviewStatus.RESOLVED
                        && r.getResolvedBy().equals(2L))
                .verifyComplete();
    }

    @Test
    void resolveIssue_auditorUser_returnsForbidden() {
        ReviewQueue rq = reviewQueueItem("TXN-0001", ReviewStatus.OPEN);
        rq.setId(ITEM_ID);
        when(reviewRepo.findById(ITEM_ID)).thenReturn(Mono.just(rq));
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);

        ResolveReviewQueueRequest req = new ResolveReviewQueueRequest();
        req.setResolutionNote("Trying to resolve.");

        StepVerifier.create(service.resolveIssue(ITEM_ID, "auditor@test.com", req))
                .expectErrorMatches(e -> e instanceof ForbiddenException
                        && e.getMessage().contains("Auditors cannot resolve issues"))
                .verify();
    }

    @Test
    void resolveIssue_alreadyResolved_returnsError() {
        ReviewQueue rq = reviewQueueItem("TXN-0001", ReviewStatus.RESOLVED);
        rq.setId(ITEM_ID);
        when(reviewRepo.findById(ITEM_ID)).thenReturn(Mono.just(rq));
        mockActiveMember("client@test.com", 1L, Role.CLIENT);

        ResolveReviewQueueRequest req = new ResolveReviewQueueRequest();
        req.setResolutionNote("Trying again.");

        StepVerifier.create(service.resolveIssue(ITEM_ID, "client@test.com", req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("already been resolved"))
                .verify();
    }

    // ──────────────────────────── helpers ────────────────────────────────────

    private void mockActiveMember(String email, Long userId, Role role) {
        User u = user(userId, email, role);
        when(userRepo.findByUsername(email)).thenReturn(Mono.just(u));
        OrganisationMember m = member(ORG_ID, userId, role);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, userId)).thenReturn(Mono.just(m));
    }

    private User user(Long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername(email);
        u.setFullName("Test User");
        u.setRole(role);
        u.setVerified(true);
        return u;
    }

    private OrganisationMember member(UUID orgId, Long userId, Role role) {
        OrganisationMember m = new OrganisationMember();
        m.setOrganisationId(orgId);
        m.setUserId(userId);
        m.setRole(role);
        m.setStatus(MemberStatus.ACTIVE);
        return m;
    }

    private Transaction txn(String id) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setOrganisationId(ORG_ID);
        t.setName("Payment");
        t.setDate(LocalDate.now());
        t.setAmount(BigDecimal.valueOf(1000));
        t.setType(TransactionType.EXPENSE);
        t.setPaymentMethod(PaymentMethod.BANK);
        t.setStatus(TransactionStatus.PENDING);
        t.setEvidenceStatus(EvidenceStatus.MISSING);
        t.setCreatedBy(1L);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private ReviewQueue reviewQueueItem(String txnId, ReviewStatus status) {
        ReviewQueue rq = new ReviewQueue();
        rq.setId(UUID.randomUUID());
        rq.setOrganisationId(ORG_ID);
        rq.setTransactionId(txnId);
        rq.setIssueType(IssueType.COMPLIANCE_ISSUE);
        rq.setDescription("Missing VAT documentation.");
        rq.setStatus(status);
        rq.setFlaggedBy("3");
        rq.setCreatedAt(LocalDateTime.now());
        return rq;
    }

    private CreateReviewQueueRequest flagRequest() {
        CreateReviewQueueRequest req = new CreateReviewQueueRequest();
        req.setOrganisationId(ORG_ID);
        req.setTransactionId("TXN-0001");
        req.setIssueType(IssueType.COMPLIANCE_ISSUE);
        req.setDescription("Missing VAT documentation.");
        return req;
    }
}
