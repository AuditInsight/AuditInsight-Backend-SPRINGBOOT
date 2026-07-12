package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.CreateTransactionRequest;
import com.diana.auditinsightbackendspringboot.DTOs.UpdateTransactionStatusRequest;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository txnRepo;
    @Mock private EvidenceRepository evidenceRepo;
    @Mock private ReviewQueueRepository reviewRepo;
    @Mock private OrganisationRepository organisationRepo;
    @Mock private OrganisationMemberRepository memberRepo;
    @Mock private UserRepository userRepo;
    @Mock private NotificationService notificationService;

    private TransactionService service;

    private final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TransactionService(txnRepo, evidenceRepo, reviewRepo, organisationRepo, memberRepo,
                userRepo, notificationService);
        lenient().when(organisationRepo.findById(ORG_ID))
                .thenReturn(Mono.just(organisation(ORG_ID, OrganisationType.PRIVATE)));
    }

    // ──────────────────────────── createTransaction ───────────────────────────

    @Test
    void createTransaction_clientUser_succeeds() {
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(txnRepo.countByOrganisationId(ORG_ID)).thenReturn(Mono.just(0L));
        when(txnRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(notificationService.notifyTransactionCreated(any(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.createTransaction("client@test.com", createRequest()))
                .expectNextMatches(r -> r.getId().equals("TXN-0001")
                        && r.getName().equals("Office Supplies")
                        && r.getCreatedBy().equals("Test User")
                        && r.getEvidenceStatus() == EvidenceStatus.MISSING
                        && r.getStatus() == TransactionStatus.PENDING)
                .verifyComplete();
    }

    @Test
    void createTransaction_memberUser_succeeds() {
        mockActiveMember("member@test.com", 2L, Role.MEMBER);
        when(txnRepo.countByOrganisationId(ORG_ID)).thenReturn(Mono.just(5L));
        when(txnRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(notificationService.notifyTransactionCreated(any(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.createTransaction("member@test.com", createRequest()))
                .expectNextMatches(r -> r.getId().equals("TXN-0006")
                        && r.getCreatedBy().equals("Test User"))
                .verifyComplete();
    }

    @Test
    void createTransaction_auditorUser_returnsForbidden() {
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);

        StepVerifier.create(service.createTransaction("auditor@test.com", createRequest()))
                .expectErrorMatches(e -> e instanceof ForbiddenException
                        && e.getMessage().contains("Auditors cannot create transactions"))
                .verify();
    }

    @Test
    void createTransaction_nonMember_returnsForbidden() {
        User user = user(4L, "stranger@test.com", Role.CLIENT);
        when(userRepo.findByUsername("stranger@test.com")).thenReturn(Mono.just(user));
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 4L)).thenReturn(Mono.empty());

        StepVerifier.create(service.createTransaction("stranger@test.com", createRequest()))
                .expectErrorMatches(e -> e instanceof ForbiddenException
                        && e.getMessage().contains("not a member"))
                .verify();
    }

    @Test
    void createTransaction_ngoOrg_missingDonorAndBudgetLine_returnsError() {
        when(organisationRepo.findById(ORG_ID)).thenReturn(Mono.just(organisation(ORG_ID, OrganisationType.NGO)));
        mockActiveMember("client@test.com", 1L, Role.CLIENT);

        StepVerifier.create(service.createTransaction("client@test.com", createRequest()))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("Donor and budget line are required"))
                .verify();
    }

    @Test
    void createTransaction_ngoOrg_withDonorAndBudgetLine_succeeds() {
        when(organisationRepo.findById(ORG_ID)).thenReturn(Mono.just(organisation(ORG_ID, OrganisationType.NGO)));
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(txnRepo.countByOrganisationId(ORG_ID)).thenReturn(Mono.just(0L));
        when(txnRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(notificationService.notifyTransactionCreated(any(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        CreateTransactionRequest req = createRequest();
        req.setDonor("UNICEF");
        req.setBudgetLine("Education Programme");

        StepVerifier.create(service.createTransaction("client@test.com", req))
                .expectNextMatches(r -> r.getDonor().equals("UNICEF")
                        && r.getBudgetLine().equals("Education Programme"))
                .verifyComplete();
    }

    @Test
    void createTransaction_privateOrg_donorAndBudgetLineNotRequired_succeeds() {
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(txnRepo.countByOrganisationId(ORG_ID)).thenReturn(Mono.just(0L));
        when(txnRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(notificationService.notifyTransactionCreated(any(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.createTransaction("client@test.com", createRequest()))
                .expectNextMatches(r -> r.getDonor() == null && r.getBudgetLine() == null)
                .verifyComplete();
    }

    @Test
    void createTransaction_organisationNotFound_returnsError() {
        when(organisationRepo.findById(ORG_ID)).thenReturn(Mono.empty());
        mockActiveMember("client@test.com", 1L, Role.CLIENT);

        StepVerifier.create(service.createTransaction("client@test.com", createRequest()))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Organisation not found"))
                .verify();
    }

    // ──────────────────────────── listTransactions ────────────────────────────

    @Test
    void listTransactions_activeMember_returnsAll() {
        mockActiveMember("member@test.com", 1L, Role.MEMBER);
        when(userRepo.findById(1L)).thenReturn(Mono.just(user(1L, "member@test.com", Role.MEMBER)));
        Transaction t1 = txn("TXN-0001");
        Transaction t2 = txn("TXN-0002");
        when(txnRepo.findAllByOrganisationId(ORG_ID)).thenReturn(Flux.just(t1, t2));

        StepVerifier.create(service.listTransactions(ORG_ID, "member@test.com"))
                .expectNextMatches(r -> r.getId().equals("TXN-0001") && r.getCreatedBy().equals("Test User"))
                .expectNextMatches(r -> r.getId().equals("TXN-0002"))
                .verifyComplete();
    }

    @Test
    void listTransactions_creatorNotFound_fallsBackToUnknown() {
        mockActiveMember("member@test.com", 1L, Role.MEMBER);
        when(userRepo.findById(1L)).thenReturn(Mono.empty());
        when(txnRepo.findAllByOrganisationId(ORG_ID)).thenReturn(Flux.just(txn("TXN-0001")));

        StepVerifier.create(service.listTransactions(ORG_ID, "member@test.com"))
                .expectNextMatches(r -> r.getCreatedBy().equals("Unknown"))
                .verifyComplete();
    }

    // ──────────────────────────── getTransaction ──────────────────────────────

    @Test
    void getTransaction_withEvidence_returnsDetailWithEvidenceList() {
        Transaction t = txn("TXN-0001");
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(userRepo.findById(1L)).thenReturn(Mono.just(user(1L, "client@test.com", Role.CLIENT)));

        Evidence ev = evidence("TXN-0001");
        when(evidenceRepo.findAllByTransactionId("TXN-0001")).thenReturn(Flux.just(ev));

        StepVerifier.create(service.getTransaction("TXN-0001", "client@test.com"))
                .expectNextMatches(r -> r.getId().equals("TXN-0001")
                        && r.getCreatedBy().equals("Test User")
                        && r.getEvidence() != null
                        && r.getEvidence().size() == 1)
                .verifyComplete();
    }

    @Test
    void getTransaction_notFound_returnsError() {
        when(txnRepo.findById("TXN-9999")).thenReturn(Mono.empty());

        StepVerifier.create(service.getTransaction("TXN-9999", "client@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Transaction not found"))
                .verify();
    }

    // ──────────────────────────── updateStatus ────────────────────────────────

    @Test
    void updateStatus_toCompleted_withMissingEvidence_autoFlags() {
        Transaction t = txn("TXN-0001");
        t.setEvidenceStatus(EvidenceStatus.MISSING);
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(txnRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepo.findById(1L)).thenReturn(Mono.just(user(1L, "client@test.com", Role.CLIENT)));

        when(reviewRepo.existsByTransactionIdAndFlaggedByAndStatus("TXN-0001", "system", ReviewStatus.OPEN))
                .thenReturn(Mono.just(false));
        when(reviewRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        UpdateTransactionStatusRequest req = new UpdateTransactionStatusRequest();
        req.setStatus(TransactionStatus.COMPLETED);

        StepVerifier.create(service.updateStatus("TXN-0001", "client@test.com", req))
                .expectNextMatches(r -> r.getStatus() == TransactionStatus.COMPLETED
                        && r.getCreatedBy().equals("Test User"))
                .verifyComplete();
    }

    @Test
    void updateStatus_auditor_returnsForbidden() {
        Transaction t = txn("TXN-0001");
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);

        UpdateTransactionStatusRequest req = new UpdateTransactionStatusRequest();
        req.setStatus(TransactionStatus.COMPLETED);

        StepVerifier.create(service.updateStatus("TXN-0001", "auditor@test.com", req))
                .expectErrorMatches(e -> e instanceof ForbiddenException
                        && e.getMessage().contains("Auditors cannot update"))
                .verify();
    }

    // ──────────────────────────── recalculateEvidenceStatus ──────────────────

    @Test
    void recalculate_zeroEvidence_setsMissing() {
        Transaction t = txn("TXN-0001");
        t.setEvidenceStatus(EvidenceStatus.PARTIAL);
        when(evidenceRepo.countByTransactionId("TXN-0001")).thenReturn(Mono.just(0L));
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));
        when(txnRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.recalculateEvidenceStatus("TXN-0001"))
                .verifyComplete();
    }

    @Test
    void recalculate_oneEvidence_setsPartial() {
        Transaction t = txn("TXN-0001");
        t.setEvidenceStatus(EvidenceStatus.MISSING);
        when(evidenceRepo.countByTransactionId("TXN-0001")).thenReturn(Mono.just(1L));
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));
        when(txnRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.recalculateEvidenceStatus("TXN-0001"))
                .verifyComplete();
    }

    @Test
    void recalculate_threeOrMoreEvidence_setsComplete_andAutoResolvesFlags() {
        Transaction t = txn("TXN-0001");
        t.setEvidenceStatus(EvidenceStatus.PARTIAL);
        when(evidenceRepo.countByTransactionId("TXN-0001")).thenReturn(Mono.just(3L));
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));
        when(txnRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ReviewQueue openFlag = reviewQueueItem("TXN-0001", IssueType.MISSING_EVIDENCE, ReviewStatus.OPEN);
        when(reviewRepo.findByTransactionIdAndStatus("TXN-0001", ReviewStatus.OPEN))
                .thenReturn(Flux.just(openFlag));
        when(reviewRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.recalculateEvidenceStatus("TXN-0001"))
                .verifyComplete();
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

    private Organisation organisation(UUID id, OrganisationType type) {
        Organisation org = new Organisation();
        org.setId(id);
        org.setIndustry(type);
        return org;
    }

    private Transaction txn(String id) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setOrganisationId(ORG_ID);
        t.setName("Office Supplies");
        t.setDate(LocalDate.now());
        t.setAmount(BigDecimal.valueOf(500));
        t.setType(TransactionType.EXPENSE);
        t.setPaymentMethod(PaymentMethod.BANK);
        t.setStatus(TransactionStatus.PENDING);
        t.setEvidenceStatus(EvidenceStatus.MISSING);
        t.setCreatedBy(1L);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private Evidence evidence(String txnId) {
        Evidence ev = new Evidence();
        ev.setId(UUID.randomUUID());
        ev.setOrganisationId(ORG_ID);
        ev.setTransactionId(txnId);
        ev.setDocumentName("Invoice");
        ev.setFolder("Sales Evidence");
        ev.setSubfolder("Sales Invoices");
        ev.setFileUpload("https://res.cloudinary.com/test/invoice.pdf");
        ev.setFileType("pdf");
        ev.setUploadedBy(1L);
        ev.setUploadedAt(LocalDateTime.now());
        return ev;
    }

    private ReviewQueue reviewQueueItem(String txnId, IssueType issueType, ReviewStatus status) {
        ReviewQueue rq = new ReviewQueue();
        rq.setId(UUID.randomUUID());
        rq.setOrganisationId(ORG_ID);
        rq.setTransactionId(txnId);
        rq.setIssueType(issueType);
        rq.setDescription("Test issue");
        rq.setStatus(status);
        rq.setFlaggedBy("system");
        rq.setCreatedAt(LocalDateTime.now());
        return rq;
    }

    private CreateTransactionRequest createRequest() {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setOrganisationId(ORG_ID);
        req.setName("Office Supplies");
        req.setDate(LocalDate.now());
        req.setAmount(BigDecimal.valueOf(500));
        req.setType(TransactionType.EXPENSE);
        req.setPaymentMethod(PaymentMethod.BANK);
        return req;
    }
}
