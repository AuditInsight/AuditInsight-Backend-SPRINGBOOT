package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Repositories.ClientRepository;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Slf4j
public class NotificationService {

    private final OrganisationRepository orgRepo;
    private final ClientRepository clientRepo;
    private final EmailService emailService;

    public NotificationService(OrganisationRepository orgRepo,
                               ClientRepository clientRepo,
                               EmailService emailService) {
        this.orgRepo = orgRepo;
        this.clientRepo = clientRepo;
        this.emailService = emailService;
    }

    private Mono<Void> withClient(UUID orgId, TriConsumer action) {
        return orgRepo.findById(orgId)
                .flatMap(org -> clientRepo.findById(org.getClientId())
                        .flatMap(client -> Mono.fromRunnable(
                                () -> action.accept(client.getEmailAddress(), client.getFirstName(), org.getName()))))
                .doOnError(e -> log.warn("Notification lookup failed for org {}: {}", orgId, e.getMessage()))
                .onErrorComplete()
                .then();
    }

    @FunctionalInterface
    private interface TriConsumer {
        void accept(String email, String clientName, String orgName);
    }

    public Mono<Void> notifyTransactionCreated(UUID orgId, UUID txnId, String txnName, String creatorName) {
        return withClient(orgId, (email, clientName, orgName) ->
                emailService.sendTransactionCreatedEmail(email, clientName, orgName, txnId.toString(), txnName, creatorName));
    }

    public Mono<Void> notifyEvidenceUploaded(UUID orgId, UUID txnId, String documentName, String uploaderName) {
        return withClient(orgId, (email, clientName, orgName) ->
                emailService.sendEvidenceUploadedEmail(email, clientName, orgName, txnId.toString(), documentName, uploaderName));
    }

    public Mono<Void> notifyIssueFlagged(UUID orgId, UUID txnId, String issueType,
                                         String description, String auditorName) {
        return withClient(orgId, (email, clientName, orgName) ->
                emailService.sendIssueFlaggedEmail(email, clientName, orgName, txnId.toString(), issueType, description, auditorName));
    }

    public Mono<Void> notifyIssueResolved(UUID orgId, UUID txnId, String resolutionNote, String resolvedByName) {
        return withClient(orgId, (email, clientName, orgName) ->
                emailService.sendIssueResolvedEmail(email, clientName, orgName, txnId.toString(), resolutionNote, resolvedByName));
    }
}