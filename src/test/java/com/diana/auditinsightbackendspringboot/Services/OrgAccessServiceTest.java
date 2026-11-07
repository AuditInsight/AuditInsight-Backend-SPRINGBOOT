package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Models.OrganisationMember;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgAccessServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganisationMemberRepository memberRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private TrialGrantRepository trialGrantRepository;
    @Mock private PlatformFreeAccessService platformFreeAccessService;

    private final UUID organisationId = UUID.randomUUID();
    private final String email = "client@test.com";
    private OrgAccessService orgAccessService;

    @BeforeEach
    void setUp() {
        orgAccessService = new OrgAccessService(userRepository, memberRepository,
                new EntitlementService(subscriptionRepository, trialGrantRepository, platformFreeAccessService));
        lenient().when(platformFreeAccessService.activeUntil()).thenReturn(Mono.just(Optional.empty()));
        lenient().when(subscriptionRepository.findAllByOrganisationIdAndStatusOrderByEndDateAsc(
                organisationId, SubscriptionStatus.ACTIVE)).thenReturn(Flux.empty());
        lenient().when(trialGrantRepository.findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.empty());

        User user = new User();
        user.setId(1L);
        user.setUsername(email);
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user));

        OrganisationMember member = new OrganisationMember();
        member.setUserId(user.getId());
        member.setOrganisationId(organisationId);
        member.setStatus(MemberStatus.ACTIVE);
        member.setRole(Role.CLIENT);
        when(memberRepository.findByOrganisationIdAndUserId(organisationId, user.getId()))
                .thenReturn(Mono.just(member));
    }

    @Test
    void allowsCurrentPaidPeriod() {
        paidPeriods(period(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectNextMatches(context -> context.role() == Role.CLIENT)
                .verifyComplete();
    }

    @Test
    void allowsUnpaidOrganisationDuringEmergencyOverride() {
        when(platformFreeAccessService.activeUntil())
                .thenReturn(Mono.just(Optional.of(LocalDateTime.now().plusDays(3))));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectNextMatches(context -> context.role() == Role.CLIENT)
                .verifyComplete();
    }

    @Test
    void allowsCurrentAuthorisedTrial() {
        TrialGrant grant = new TrialGrant();
        grant.setStatus(TrialGrantStatus.ACTIVE);
        grant.setStartsAt(LocalDateTime.now().minusDays(1));
        grant.setEndsAt(LocalDateTime.now().plusDays(5));
        when(trialGrantRepository.findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.just(grant));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectNextMatches(context -> context.role() == Role.CLIENT)
                .verifyComplete();
    }

    @Test
    void rejectsExpiredTrialGrant() {
        TrialGrant grant = new TrialGrant();
        grant.setStatus(TrialGrantStatus.ACTIVE);
        grant.setStartsAt(LocalDateTime.now().minusDays(10));
        grant.setEndsAt(LocalDateTime.now().minusMinutes(1));
        when(trialGrantRepository.findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.just(grant));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectErrorMatches(error -> error instanceof ForbiddenException
                        && error.getMessage().contains("paid subscription is required"))
                .verify();
    }

    @Test
    void rejectsPaidPeriodThatHasNotStartedYet() {
        paidPeriods(period(LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(32)));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void rejectsExpiredPaidPeriod() {
        paidPeriods(period(LocalDateTime.now().minusDays(31), LocalDateTime.now().minusDays(1)));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void rejectsOrganisationWithoutAnyAccess() {
        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectError(ForbiddenException.class)
                .verify();
    }

    private void paidPeriods(Subscription... periods) {
        when(subscriptionRepository.findAllByOrganisationIdAndStatusOrderByEndDateAsc(
                organisationId, SubscriptionStatus.ACTIVE)).thenReturn(Flux.just(periods));
    }

    private Subscription period(LocalDateTime start, LocalDateTime end) {
        Subscription subscription = new Subscription();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(start);
        subscription.setEndDate(end);
        return subscription;
    }
}
