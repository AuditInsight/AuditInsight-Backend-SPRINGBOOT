package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Models.OrganisationMember;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgAccessServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganisationMemberRepository memberRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlatformFreeAccessService platformFreeAccessService;

    private final UUID organisationId = UUID.randomUUID();
    private final String email = "client@test.com";
    private OrgAccessService orgAccessService;

    @BeforeEach
    void setUp() {
        orgAccessService = new OrgAccessService(
                userRepository, memberRepository, subscriptionRepository, platformFreeAccessService);
        when(platformFreeAccessService.isEnabled()).thenReturn(Mono.just(false));

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
    void resolveGatedMember_allowsCurrentPaidSubscription() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(30));
        when(subscriptionRepository.findFirstByOrganisationIdOrderByCreatedAtDesc(organisationId))
                .thenReturn(Mono.just(subscription));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectNextMatches(context -> context.role() == Role.CLIENT)
                .verifyComplete();
    }

    @Test
    void resolveGatedMember_allowsUnpaidOrganisationDuringPlatformFreeAccess() {
        when(platformFreeAccessService.isEnabled()).thenReturn(Mono.just(true));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectNextMatches(context -> context.role() == Role.CLIENT)
                .verifyComplete();
    }

    @Test
    void resolveGatedMember_rejectsUnpaidTrialEvenWhileTrialPeriodIsCurrent() {
        Subscription trial = subscription(SubscriptionStatus.TRIAL, LocalDateTime.now().plusDays(30));
        when(subscriptionRepository.findFirstByOrganisationIdOrderByCreatedAtDesc(organisationId))
                .thenReturn(Mono.just(trial));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectErrorMatches(error -> error instanceof ForbiddenException
                        && error.getMessage().contains("paid subscription is required"))
                .verify();
    }

    @Test
    void resolveGatedMember_rejectsExpiredPaidSubscription() {
        Subscription expired = subscription(SubscriptionStatus.ACTIVE, LocalDateTime.now().minusDays(1));
        when(subscriptionRepository.findFirstByOrganisationIdOrderByCreatedAtDesc(organisationId))
                .thenReturn(Mono.just(expired));

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void resolveGatedMember_rejectsOrganisationWithoutSubscription() {
        when(subscriptionRepository.findFirstByOrganisationIdOrderByCreatedAtDesc(organisationId))
                .thenReturn(Mono.empty());

        StepVerifier.create(orgAccessService.resolveGatedMember(organisationId, email))
                .expectError(ForbiddenException.class)
                .verify();
    }

    private Subscription subscription(SubscriptionStatus status, LocalDateTime endDate) {
        Subscription subscription = new Subscription();
        subscription.setStatus(status);
        subscription.setEndDate(endDate);
        return subscription;
    }
}
