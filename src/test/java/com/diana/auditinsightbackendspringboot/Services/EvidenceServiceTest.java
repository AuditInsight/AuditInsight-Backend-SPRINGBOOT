package com.diana.auditinsightbackendspringboot.Services;

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
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    @Mock private EvidenceRepository evidenceRepo;
    @Mock private TransactionRepository txnRepo;
    @Mock private OrganisationRepository organisationRepo;
    @Mock private OrganisationMemberRepository memberRepo;
    @Mock private UserRepository userRepo;
    @Mock private TransactionService txnService;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private NotificationService notificationService;

    private EvidenceService service;

    private final UUID ORG_ID = UUID.randomUUID();
    private final UUID EV_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EvidenceService(evidenceRepo, txnRepo, organisationRepo, memberRepo, userRepo,
                txnService, cloudinaryService, notificationService);
        lenient().when(organisationRepo.findById(ORG_ID))
                .thenReturn(Mono.just(organisation(ORG_ID, OrganisationType.PRIVATE)));
    }

    // ──────────────────────────── uploadEvidence ──────────────────────────────

    @Test
    void uploadEvidence_validFile_clientUser_succeeds() {
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(txn("TXN-0001")));
        when(cloudinaryService.upload(any(byte[].class), anyString()))
                .thenReturn(Mono.just("https://res.cloudinary.com/test/invoice.pdf"));
        when(evidenceRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(txnService.recalculateEvidenceStatus("TXN-0001")).thenReturn(Mono.empty());
        when(notificationService.notifyEvidenceUploaded(any(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("invoice.pdf"),
                        ORG_ID, "TXN-0001", "Supplier Invoice",
                        "Purchases and Procurement", "Supplier Invoices", null))
                .expectNextMatches(r -> r.getFileType().equals("pdf")
                        && r.getFileUpload().equals("https://res.cloudinary.com/test/invoice.pdf")
                        && r.getDocumentName().equals("Supplier Invoice"))
                .verifyComplete();
    }

    @Test
    void uploadEvidence_jpegFile_succeeds() {
        mockActiveMember("member@test.com", 2L, Role.MEMBER);
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(txn("TXN-0001")));
        when(cloudinaryService.upload(any(byte[].class), anyString()))
                .thenReturn(Mono.just("https://res.cloudinary.com/test/receipt.jpg"));
        when(evidenceRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(txnService.recalculateEvidenceStatus("TXN-0001")).thenReturn(Mono.empty());
        when(notificationService.notifyEvidenceUploaded(any(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.uploadEvidence("member@test.com", filePart("receipt.jpg"),
                        ORG_ID, "TXN-0001", "Receipt",
                        "Sales Evidence", "Receipts", "Monthly receipt"))
                .expectNextMatches(r -> r.getFileType().equals("jpg"))
                .verifyComplete();
    }

    @Test
    void uploadEvidence_disallowedFileType_returnsError() {
        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("report.docx"),
                        ORG_ID, "TXN-0001", "Report",
                        "Purchases and Procurement", "Supplier Invoices", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("docx")
                        && e.getMessage().contains("not allowed"))
                .verify();
    }

    @Test
    void uploadEvidence_noExtension_returnsError() {
        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("noextension"),
                        ORG_ID, "TXN-0001", "Doc",
                        "Purchases and Procurement", "Supplier Invoices", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("must have an extension"))
                .verify();
    }

    @Test
    void uploadEvidence_invalidFolder_returnsError() {
        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("invoice.pdf"),
                        ORG_ID, "TXN-0001", "Invoice",
                        "Invalid Folder", "Wrong Subfolder", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("Invalid folder or subfolder"))
                .verify();
    }

    @Test
    void uploadEvidence_invalidSubfolder_returnsError() {
        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("invoice.pdf"),
                        ORG_ID, "TXN-0001", "Invoice",
                        "Purchases and Procurement", "Wrong Subfolder", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("Invalid folder or subfolder"))
                .verify();
    }

    @Test
    void uploadEvidence_auditorUser_returnsForbidden() {
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);

        StepVerifier.create(service.uploadEvidence("auditor@test.com", filePart("invoice.pdf"),
                        ORG_ID, "TXN-0001", "Invoice",
                        "Purchases and Procurement", "Supplier Invoices", null))
                .expectErrorMatches(e -> e instanceof ForbiddenException
                        && e.getMessage().contains("Auditors cannot upload evidence"))
                .verify();
    }

    @Test
    void uploadEvidence_transactionFromDifferentOrg_returnsError() {
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        Transaction t = txn("TXN-0001");
        t.setOrganisationId(UUID.randomUUID()); // different org
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(t));

        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("invoice.pdf"),
                        ORG_ID, "TXN-0001", "Invoice",
                        "Purchases and Procurement", "Supplier Invoices", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("does not belong to this organisation"))
                .verify();
    }

    @Test
    void uploadEvidence_transactionNotFound_returnsError() {
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.empty());

        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("invoice.pdf"),
                        ORG_ID, "TXN-0001", "Invoice",
                        "Purchases and Procurement", "Supplier Invoices", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Transaction not found"))
                .verify();
    }

    @Test
    void uploadEvidence_organisationNotFound_returnsError() {
        when(organisationRepo.findById(ORG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("invoice.pdf"),
                        ORG_ID, "TXN-0001", "Invoice",
                        "Purchases and Procurement", "Supplier Invoices", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Organisation not found"))
                .verify();
    }

    @Test
    void uploadEvidence_ngoOrg_ngoFolder_succeeds() {
        when(organisationRepo.findById(ORG_ID)).thenReturn(Mono.just(organisation(ORG_ID, OrganisationType.NGO)));
        mockActiveMember("client@test.com", 1L, Role.CLIENT);
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(txn("TXN-0001")));
        when(cloudinaryService.upload(any(byte[].class), anyString()))
                .thenReturn(Mono.just("https://res.cloudinary.com/test/donor-report.pdf"));
        when(evidenceRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(txnService.recalculateEvidenceStatus("TXN-0001")).thenReturn(Mono.empty());
        when(notificationService.notifyEvidenceUploaded(any(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("donor-report.pdf"),
                        ORG_ID, "TXN-0001", "Donor Report",
                        "Financial Reporting", "Donor Financial Reports", null))
                .expectNextMatches(r -> r.getFileType().equals("pdf"))
                .verifyComplete();
    }

    @Test
    void uploadEvidence_ngoFolder_rejectedForPrivateOrg() {
        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("donor-report.pdf"),
                        ORG_ID, "TXN-0001", "Donor Report",
                        "Financial Reporting", "Donor Financial Reports", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("Invalid folder or subfolder"))
                .verify();
    }

    @Test
    void uploadEvidence_privateFolder_rejectedForNgoOrg() {
        when(organisationRepo.findById(ORG_ID)).thenReturn(Mono.just(organisation(ORG_ID, OrganisationType.NGO)));

        StepVerifier.create(service.uploadEvidence("client@test.com", filePart("invoice.pdf"),
                        ORG_ID, "TXN-0001", "Invoice",
                        "Sales Evidence", "Receipts", null))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("Invalid folder or subfolder"))
                .verify();
    }

    // ──────────────────────────── listByOrg ──────────────────────────────────

    @Test
    void listByOrg_activeMember_returnsAll() {
        mockActiveMember("member@test.com", 1L, Role.MEMBER);
        when(evidenceRepo.findAllByOrganisationId(ORG_ID))
                .thenReturn(Flux.just(evidence(EV_ID, "TXN-0001"), evidence(UUID.randomUUID(), "TXN-0002")));

        StepVerifier.create(service.listByOrg(ORG_ID, "member@test.com"))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void listByOrg_auditorCanView() {
        mockActiveMember("auditor@test.com", 3L, Role.AUDITOR);
        when(evidenceRepo.findAllByOrganisationId(ORG_ID))
                .thenReturn(Flux.just(evidence(EV_ID, "TXN-0001")));

        StepVerifier.create(service.listByOrg(ORG_ID, "auditor@test.com"))
                .expectNextCount(1)
                .verifyComplete();
    }

    // ──────────────────────────── getEvidence ────────────────────────────────

    @Test
    void getEvidence_activeMember_returnsRecord() {
        Evidence ev = evidence(EV_ID, "TXN-0001");
        when(evidenceRepo.findById(EV_ID)).thenReturn(Mono.just(ev));
        mockActiveMember("client@test.com", 1L, Role.CLIENT);

        StepVerifier.create(service.getEvidence(EV_ID, "client@test.com"))
                .expectNextMatches(r -> r.getId().equals(EV_ID))
                .verifyComplete();
    }

    @Test
    void getEvidence_notFound_returnsError() {
        when(evidenceRepo.findById(EV_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getEvidence(EV_ID, "client@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Evidence not found"))
                .verify();
    }

    // ──────────────────────────── listByTransaction ───────────────────────────

    @Test
    void listByTransaction_activeMember_returnsEvidence() {
        when(txnRepo.findById("TXN-0001")).thenReturn(Mono.just(txn("TXN-0001")));
        mockActiveMember("member@test.com", 1L, Role.MEMBER);
        when(evidenceRepo.findAllByTransactionId("TXN-0001"))
                .thenReturn(Flux.just(evidence(EV_ID, "TXN-0001")));

        StepVerifier.create(service.listByTransaction("TXN-0001", "member@test.com"))
                .expectNextMatches(r -> r.getTransactionId().equals("TXN-0001"))
                .verifyComplete();
    }

    // ──────────────────────────── helpers ────────────────────────────────────

    private void mockActiveMember(String email, Long userId, Role role) {
        User u = user(userId, email, role);
        when(userRepo.findByUsername(email)).thenReturn(Mono.just(u));
        OrganisationMember m = member(ORG_ID, userId, role);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, userId)).thenReturn(Mono.just(m));
    }

    private FilePart filePart(String filename) {
        FilePart fp = mock(FilePart.class);
        lenient().when(fp.filename()).thenReturn(filename);
        DataBuffer buf = new DefaultDataBufferFactory().wrap("test-file-content".getBytes());
        lenient().when(fp.content()).thenReturn(Flux.just(buf));
        return fp;
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

    private Organisation organisation(UUID id, OrganisationType type) {
        Organisation org = new Organisation();
        org.setId(id);
        org.setIndustry(type);
        return org;
    }

    private Evidence evidence(UUID id, String txnId) {
        Evidence ev = new Evidence();
        ev.setId(id);
        ev.setOrganisationId(ORG_ID);
        ev.setTransactionId(txnId);
        ev.setDocumentName("Supplier Invoice");
        ev.setFolder("Purchases and Procurement");
        ev.setSubfolder("Supplier Invoices");
        ev.setFileUpload("https://res.cloudinary.com/test/invoice.pdf");
        ev.setFileType("pdf");
        ev.setUploadedBy(1L);
        ev.setUploadedAt(LocalDateTime.now());
        return ev;
    }
}
