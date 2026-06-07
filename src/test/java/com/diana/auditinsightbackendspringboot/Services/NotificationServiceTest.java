package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.ClientProfile;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Repositories.ClientRepository;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private OrganisationRepository orgRepo;
    @Mock private ClientRepository clientRepo;
    @Mock private EmailService emailService;

    private NotificationService service;

    private final UUID ORG_ID    = UUID.randomUUID();
    private final UUID CLIENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationService(orgRepo, clientRepo, emailService);
    }

    // ──────────────────────────── notifyTransactionCreated ───────────────────

    @Test
    void notifyTransactionCreated_sendsCorrectEmail() {
        stubOrgAndClient("Acme Corp", "raissa@test.com", "Raissa");

        StepVerifier.create(service.notifyTransactionCreated(ORG_ID, "TXN-0001", "Office Supplies", "John"))
                .verifyComplete();

        verify(emailService).sendTransactionCreatedEmail(
                "raissa@test.com", "Raissa", "Acme Corp", "TXN-0001", "Office Supplies", "John");
    }

    // ──────────────────────────── notifyEvidenceUploaded ─────────────────────

    @Test
    void notifyEvidenceUploaded_sendsCorrectEmail() {
        stubOrgAndClient("Acme Corp", "raissa@test.com", "Raissa");

        StepVerifier.create(service.notifyEvidenceUploaded(ORG_ID, "TXN-0001", "Bank Statement", "Diana"))
                .verifyComplete();

        verify(emailService).sendEvidenceUploadedEmail(
                "raissa@test.com", "Raissa", "Acme Corp", "TXN-0001", "Bank Statement", "Diana");
    }

    // ──────────────────────────── notifyIssueFlagged ─────────────────────────

    @Test
    void notifyIssueFlagged_sendsCorrectEmail() {
        stubOrgAndClient("Acme Corp", "raissa@test.com", "Raissa");

        StepVerifier.create(service.notifyIssueFlagged(
                        ORG_ID, "TXN-0001", "MISSING_EVIDENCE", "No supporting documents found.", "Auditor Mike"))
                .verifyComplete();

        verify(emailService).sendIssueFlaggedEmail(
                "raissa@test.com", "Raissa", "Acme Corp",
                "TXN-0001", "MISSING_EVIDENCE", "No supporting documents found.", "Auditor Mike");
    }

    // ──────────────────────────── notifyIssueResolved ────────────────────────

    @Test
    void notifyIssueResolved_sendsCorrectEmail() {
        stubOrgAndClient("Acme Corp", "raissa@test.com", "Raissa");

        StepVerifier.create(service.notifyIssueResolved(
                        ORG_ID, "TXN-0001", "Evidence reviewed and accepted.", "Diana"))
                .verifyComplete();

        verify(emailService).sendIssueResolvedEmail(
                "raissa@test.com", "Raissa", "Acme Corp",
                "TXN-0001", "Evidence reviewed and accepted.", "Diana");
    }

    // ──────────────────────────── graceful failure ───────────────────────────

    @Test
    void notify_orgNotFound_completesWithoutErrorAndNoEmailSent() {
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.notifyTransactionCreated(ORG_ID, "TXN-0001", "Office Supplies", "John"))
                .verifyComplete();

        verify(emailService, never()).sendTransactionCreatedEmail(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void notify_clientProfileNotFound_completesWithoutErrorAndNoEmailSent() {
        Organisation organisation = org(ORG_ID, CLIENT_ID, "Acme Corp");
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(organisation));
        when(clientRepo.findById(CLIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.notifyEvidenceUploaded(ORG_ID, "TXN-0001", "Invoice", "Diana"))
                .verifyComplete();

        verify(emailService, never()).sendEvidenceUploadedEmail(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void notify_orgLookupThrows_completesWithoutError() {
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.error(new RuntimeException("DB unavailable")));

        StepVerifier.create(service.notifyIssueFlagged(
                        ORG_ID, "TXN-0001", "COMPLIANCE_ISSUE", "VAT missing", "Auditor"))
                .verifyComplete();
    }

    // ──────────────────────────── helpers ────────────────────────────────────

    private void stubOrgAndClient(String orgName, String clientEmail, String clientFirstName) {
        Organisation org = org(ORG_ID, CLIENT_ID, orgName);
        ClientProfile client = client(CLIENT_ID, clientEmail, clientFirstName);
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(org));
        when(clientRepo.findById(CLIENT_ID)).thenReturn(Mono.just(client));
    }

    private Organisation org(UUID id, UUID clientId, String name) {
        Organisation o = new Organisation();
        o.setId(id);
        o.setClientId(clientId);
        o.setName(name);
        return o;
    }

    private ClientProfile client(UUID id, String email, String firstName) {
        ClientProfile c = new ClientProfile();
        c.setId(id);
        c.setEmailAddress(email);
        c.setFirstName(firstName);
        c.setLastName("Test");
        return c;
    }
}
