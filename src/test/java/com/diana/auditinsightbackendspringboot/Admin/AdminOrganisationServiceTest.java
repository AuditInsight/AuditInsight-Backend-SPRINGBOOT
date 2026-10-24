package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminChangeSubscriptionRequest;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import com.diana.auditinsightbackendspringboot.Services.SubscriptionActivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrganisationServiceTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private OrganisationMemberRepository organisationMemberRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionActivationService activationService;
    @Mock private AdminAuditService adminAuditService;

    private AdminOrganisationService adminOrganisationService;

    private final UUID orgId = UUID.randomUUID();
    private static final String ACTING_ADMIN = "info@auditinsight.tech";

    @BeforeEach
    void setUp() {
        adminOrganisationService = new AdminOrganisationService(organisationRepository, organisationMemberRepository,
                subscriptionRepository, paymentRepository, userRepository, activationService, adminAuditService);
    }

    private Organisation organisation() {
        Organisation o = new Organisation();
        o.setId(orgId);
        o.setName("Acme NGO");
        return o;
    }

    private User admin() {
        User u = new User();
        u.setId(1L);
        u.setUsername(ACTING_ADMIN);
        u.setRole(Role.ADMIN);
        return u;
    }

    @Test
    void changeSubscription_organisationHasNoActiveSubscription_capturesNullBeforeState() {
        when(organisationRepository.findById(orgId)).thenReturn(Mono.just(organisation()));
        when(userRepository.findByUsername(ACTING_ADMIN)).thenReturn(Mono.just(admin()));
        when(subscriptionRepository.findByOrganisationIdAndStatus(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Mono.empty());

        Subscription after = new Subscription();
        after.setId(UUID.randomUUID());
        after.setOrganisationId(orgId);
        after.setSubscriptionType(SubscriptionType.ANNUAL);
        after.setStatus(SubscriptionStatus.ACTIVE);
        after.setStartDate(LocalDateTime.now());
        after.setEndDate(LocalDateTime.now().plusDays(365));
        when(activationService.adminChangeSubscription(orgId, SubscriptionType.ANNUAL, null, 1L))
                .thenReturn(Mono.just(after));
        when(adminAuditService.record(eq(1L), eq("CHANGE_SUBSCRIPTION"), eq("ORGANISATION"), eq(orgId.toString()),
                isNull(), any())).thenReturn(Mono.just(new AdminAuditLog()));

        AdminChangeSubscriptionRequest request = new AdminChangeSubscriptionRequest();
        request.setSubscriptionType(SubscriptionType.ANNUAL);

        StepVerifier.create(adminOrganisationService.changeSubscription(orgId, request, ACTING_ADMIN))
                .expectNextMatches(summary -> summary.getSubscriptionType() == SubscriptionType.ANNUAL
                        && summary.getStatus() == SubscriptionStatus.ACTIVE)
                .verifyComplete();

        verify(adminAuditService).record(eq(1L), eq("CHANGE_SUBSCRIPTION"), eq("ORGANISATION"),
                eq(orgId.toString()), isNull(), any());
    }

    @Test
    void changeSubscription_organisationHasActiveSubscription_recordsBeforeAndAfterState() {
        when(organisationRepository.findById(orgId)).thenReturn(Mono.just(organisation()));
        when(userRepository.findByUsername(ACTING_ADMIN)).thenReturn(Mono.just(admin()));

        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setOrganisationId(orgId);
        existing.setSubscriptionType(SubscriptionType.MONTHLY);
        existing.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByOrganisationIdAndStatus(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Mono.just(existing));

        Subscription after = new Subscription();
        after.setId(existing.getId());
        after.setOrganisationId(orgId);
        after.setSubscriptionType(SubscriptionType.SIX_MONTHS);
        after.setStatus(SubscriptionStatus.ACTIVE);
        when(activationService.adminChangeSubscription(orgId, SubscriptionType.SIX_MONTHS, null, 1L))
                .thenReturn(Mono.just(after));
        when(adminAuditService.record(eq(1L), eq("CHANGE_SUBSCRIPTION"), eq("ORGANISATION"),
                eq(orgId.toString()), any(), any())).thenReturn(Mono.just(new AdminAuditLog()));

        AdminChangeSubscriptionRequest request = new AdminChangeSubscriptionRequest();
        request.setSubscriptionType(SubscriptionType.SIX_MONTHS);

        StepVerifier.create(adminOrganisationService.changeSubscription(orgId, request, ACTING_ADMIN))
                .expectNextMatches(summary -> summary.getSubscriptionType() == SubscriptionType.SIX_MONTHS)
                .verifyComplete();

        verify(activationService).adminChangeSubscription(orgId, SubscriptionType.SIX_MONTHS, null, 1L);
    }

    @Test
    void changeSubscription_unknownOrganisation_emitsInvalidRecord() {
        when(organisationRepository.findById(orgId)).thenReturn(Mono.empty());

        AdminChangeSubscriptionRequest request = new AdminChangeSubscriptionRequest();
        request.setSubscriptionType(SubscriptionType.MONTHLY);

        StepVerifier.create(adminOrganisationService.changeSubscription(orgId, request, ACTING_ADMIN))
                .expectError(InvalidRecord.class)
                .verify();

        verify(activationService, never()).adminChangeSubscription(any(), any(), any(), any());
    }
}
