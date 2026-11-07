package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Admin.AdminAuditService;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialEventType;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialSource;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.TrialCampaign;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Models.TrialGrantEvent;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantEventRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialGrantServiceTest {

    @Mock private TrialGrantRepository grantRepo;
    @Mock private TrialGrantEventRepository eventRepo;
    @Mock private OrganisationRepository organisationRepo;
    @Mock private SubscriptionRepository subscriptionRepo;
    @Mock private UserRepository userRepo;
    @Mock private AdminAuditService adminAuditService;
    @Mock private OrganisationLock organisationLock;

    private TrialGrantService service;

    private final UUID ORG_ID = UUID.randomUUID();
    private final UUID OWNER_ID = UUID.randomUUID();
    private static final String ADMIN = "admin@auditinsight.test";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TrialGrantService(grantRepo, eventRepo, organisationRepo, subscriptionRepo, userRepo,
                adminAuditService, organisationLock);

        lenient().when(organisationLock.inTransaction(any(), any()))
                .thenAnswer(inv -> ((Supplier<Mono<?>>) inv.getArgument(1)).get());
        User admin = new User();
        admin.setId(99L);
        admin.setUsername(ADMIN);
        lenient().when(userRepo.findByUsername(ADMIN)).thenReturn(Mono.just(admin));
        Organisation org = new Organisation();
        org.setId(ORG_ID);
        org.setClientId(OWNER_ID);
        lenient().when(organisationRepo.findById(ORG_ID)).thenReturn(Mono.just(org));
        lenient().when(grantRepo.save(any())).thenAnswer(inv -> {
            TrialGrant g = inv.getArgument(0);
            if (g.getId() == null) g.setId(UUID.randomUUID());
            return Mono.just(g);
        });
        lenient().when(eventRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(subscriptionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(adminAuditService.record(any(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.just(new AdminAuditLog()));
        lenient().when(subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(ORG_ID, SubscriptionStatus.ACTIVE))
                .thenReturn(Flux.empty());
    }

    // ── grant ─────────────────────────────────────────────────────────────────────

    @Test
    void grant_createsTrialOfRequestedLengthAndAudits() {
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE)).thenReturn(Mono.empty());

        StepVerifier.create(service.grant(ORG_ID, 14, "Pilot customer", ADMIN))
                .assertNext(g -> {
                    assertThat(g.getSource()).isEqualTo(TrialSource.INDIVIDUAL);
                    assertThat(g.getStatus()).isEqualTo(TrialGrantStatus.ACTIVE);
                    assertThat(g.getOwnerClientId()).isEqualTo(OWNER_ID);
                    assertThat(Duration.between(g.getStartsAt(), g.getEndsAt())).isEqualTo(Duration.ofDays(14));
                    assertThat(g.getGrantedBy()).isEqualTo(99L);
                })
                .verifyComplete();
        verify(adminAuditService).record(eq(99L), eq("GRANT_TRIAL"), eq("ORGANISATION"), eq(ORG_ID.toString()),
                any(), any(), eq("Pilot customer"));
    }

    @Test
    void grant_rejectsWhenATrialIsAlreadyRunning() {
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.just(grant(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6))));

        StepVerifier.create(service.grant(ORG_ID, 7, null, ADMIN))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("already has an active trial"))
                .verify();
        verify(adminAuditService, never()).record(any(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void grant_closesAnExpiredOpenGrantFirst() {
        TrialGrant expired = grant(LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(3));
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.just(expired), Mono.empty());

        StepVerifier.create(service.grant(ORG_ID, 7, "Second chance", ADMIN))
                .assertNext(g -> assertThat(g.getId()).isNotEqualTo(expired.getId()))
                .verifyComplete();
        assertThat(expired.getStatus()).isEqualTo(TrialGrantStatus.ENDED);
    }

    @Test
    void grant_rejectsOutOfRangeDurations() {
        StepVerifier.create(service.grant(ORG_ID, 0, null, ADMIN)).expectError(InvalidRecord.class).verify();
        StepVerifier.create(service.grant(ORG_ID, 366, null, ADMIN)).expectError(InvalidRecord.class).verify();
    }

    // ── extend / shorten ──────────────────────────────────────────────────────────

    @Test
    void extend_movesPaidTimeBoughtDuringTheTrialLater() {
        LocalDateTime trialEnd = LocalDateTime.now().plusDays(5);
        TrialGrant current = grant(LocalDateTime.now().minusDays(2), trialEnd);
        current.setReminderSent(true);
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE)).thenReturn(Mono.just(current));
        Subscription scheduled = period(trialEnd, trialEnd.plusDays(30));
        when(subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(ORG_ID, SubscriptionStatus.ACTIVE))
                .thenReturn(Flux.just(scheduled));

        LocalDateTime newEnd = trialEnd.plusDays(10);
        StepVerifier.create(service.changeEnd(ORG_ID, newEnd, "Onboarding delayed", ADMIN))
                .assertNext(g -> assertThat(g.getEndsAt()).isEqualTo(newEnd))
                .verifyComplete();

        assertThat(scheduled.getStartDate()).isEqualTo(newEnd);
        assertThat(scheduled.getEndDate()).isEqualTo(newEnd.plusDays(30));
        assertThat(current.isReminderSent()).isFalse();
        verify(adminAuditService).record(eq(99L), eq("EXTEND_TRIAL"), any(), any(), any(), any(), eq("Onboarding delayed"));
        ArgumentCaptor<TrialGrantEvent> events = ArgumentCaptor.forClass(TrialGrantEvent.class);
        verify(eventRepo, atLeastOnce()).save(events.capture());
        assertThat(events.getAllValues()).extracting(TrialGrantEvent::getEventType)
                .containsExactly(TrialEventType.EXTENDED, TrialEventType.PAID_PERIODS_MOVED);
    }

    @Test
    void shorten_movesPaidTimeEarlierButLeavesRunningPeriodsAlone() {
        LocalDateTime trialEnd = LocalDateTime.now().plusDays(10);
        TrialGrant current = grant(LocalDateTime.now().minusDays(2), trialEnd);
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE)).thenReturn(Mono.just(current));
        Subscription running = period(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(29));
        LocalDateTime runningStart = running.getStartDate();
        Subscription scheduled = period(trialEnd, trialEnd.plusDays(30));
        when(subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(ORG_ID, SubscriptionStatus.ACTIVE))
                .thenReturn(Flux.just(running, scheduled));

        LocalDateTime newEnd = trialEnd.minusDays(7);
        StepVerifier.create(service.changeEnd(ORG_ID, newEnd, "Agreed shorter pilot", ADMIN))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(scheduled.getStartDate()).isEqualTo(newEnd);
        assertThat(scheduled.getEndDate()).isEqualTo(newEnd.plusDays(30));
        assertThat(running.getStartDate()).isEqualTo(runningStart);
        verify(adminAuditService).record(eq(99L), eq("SHORTEN_TRIAL"), any(), any(), any(), any(), any());
    }

    @Test
    void changeEnd_requiresReasonAndFutureDate() {
        StepVerifier.create(service.changeEnd(ORG_ID, LocalDateTime.now().plusDays(3), " ", ADMIN))
                .expectError(InvalidRecord.class).verify();
        StepVerifier.create(service.changeEnd(ORG_ID, LocalDateTime.now().minusDays(1), "x", ADMIN))
                .expectError(InvalidRecord.class).verify();
    }

    @Test
    void changeEnd_capsTrialAt365Days() {
        TrialGrant current = grant(LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(5));
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE)).thenReturn(Mono.just(current));

        StepVerifier.create(service.changeEnd(ORG_ID, current.getStartsAt().plusDays(366), "too long", ADMIN))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("365"))
                .verify();
    }

    @Test
    void changeEnd_failsWithoutACurrentTrial() {
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE)).thenReturn(Mono.empty());

        StepVerifier.create(service.changeEnd(ORG_ID, LocalDateTime.now().plusDays(3), "x", ADMIN))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("no active trial"))
                .verify();
    }

    // ── revoke ────────────────────────────────────────────────────────────────────

    @Test
    void revoke_endsTrialAndPaidTimeStartsNow() {
        LocalDateTime trialEnd = LocalDateTime.now().plusDays(5);
        TrialGrant current = grant(LocalDateTime.now().minusDays(2), trialEnd);
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE)).thenReturn(Mono.just(current));
        Subscription scheduled = period(trialEnd, trialEnd.plusDays(30));
        when(subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(ORG_ID, SubscriptionStatus.ACTIVE))
                .thenReturn(Flux.just(scheduled));

        LocalDateTime before = LocalDateTime.now();
        StepVerifier.create(service.revoke(ORG_ID, "Misuse", ADMIN))
                .assertNext(g -> {
                    assertThat(g.getStatus()).isEqualTo(TrialGrantStatus.REVOKED);
                    assertThat(g.getRevokedBy()).isEqualTo(99L);
                    assertThat(g.getRevokeReason()).isEqualTo("Misuse");
                })
                .verifyComplete();

        assertThat(scheduled.getStartDate()).isBetween(before.minusSeconds(1), LocalDateTime.now());
        assertThat(Duration.between(scheduled.getStartDate(), scheduled.getEndDate())).isEqualTo(Duration.ofDays(30));
        verify(adminAuditService).record(eq(99L), eq("REVOKE_TRIAL"), any(), any(), any(), any(), eq("Misuse"));
    }

    @Test
    void revoke_requiresReason() {
        StepVerifier.create(service.revoke(ORG_ID, null, ADMIN)).expectError(InvalidRecord.class).verify();
    }

    // ── campaign grants ───────────────────────────────────────────────────────────

    @Test
    void grantFromCampaign_grantsCampaignLength() {
        when(grantRepo.existsByOwnerClientIdAndSource(OWNER_ID, TrialSource.CAMPAIGN)).thenReturn(Mono.just(false));
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE)).thenReturn(Mono.empty());

        StepVerifier.create(service.grantFromCampaign(org(), campaign(30)))
                .assertNext(g -> {
                    assertThat(g.getSource()).isEqualTo(TrialSource.CAMPAIGN);
                    assertThat(Duration.between(g.getStartsAt(), g.getEndsAt())).isEqualTo(Duration.ofDays(30));
                })
                .verifyComplete();
    }

    @Test
    void grantFromCampaign_skipsOwnerWhoAlreadyHadOne() {
        when(grantRepo.existsByOwnerClientIdAndSource(OWNER_ID, TrialSource.CAMPAIGN)).thenReturn(Mono.just(true));

        StepVerifier.create(service.grantFromCampaign(org(), campaign(30))).verifyComplete();
        verify(grantRepo, never()).save(any());
    }

    @Test
    void grantFromCampaign_concurrentDuplicateIsSkippedNotFailed() {
        when(grantRepo.existsByOwnerClientIdAndSource(OWNER_ID, TrialSource.CAMPAIGN)).thenReturn(Mono.just(false));
        when(grantRepo.findFirstByOrganisationIdAndStatus(ORG_ID, TrialGrantStatus.ACTIVE)).thenReturn(Mono.empty());
        org.mockito.Mockito.doReturn(Mono.error(new DataIntegrityViolationException("duplicate"))).when(grantRepo).save(any());

        StepVerifier.create(service.grantFromCampaign(org(), campaign(30))).verifyComplete();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private Organisation org() {
        Organisation org = new Organisation();
        org.setId(ORG_ID);
        org.setClientId(OWNER_ID);
        return org;
    }

    private TrialCampaign campaign(int days) {
        TrialCampaign c = new TrialCampaign();
        c.setId(UUID.randomUUID());
        c.setName("Launch");
        c.setDurationDays(days);
        c.setStartsAt(LocalDateTime.now().minusDays(1));
        c.setEnabled(true);
        c.setCreatedBy(99L);
        return c;
    }

    private TrialGrant grant(LocalDateTime start, LocalDateTime end) {
        TrialGrant g = new TrialGrant();
        g.setId(UUID.randomUUID());
        g.setOrganisationId(ORG_ID);
        g.setOwnerClientId(OWNER_ID);
        g.setSource(TrialSource.INDIVIDUAL);
        g.setStatus(TrialGrantStatus.ACTIVE);
        g.setStartsAt(start);
        g.setEndsAt(end);
        g.setDurationDays((int) Duration.between(start, end).toDays());
        return g;
    }

    private Subscription period(LocalDateTime start, LocalDateTime end) {
        Subscription s = new Subscription();
        s.setId(UUID.randomUUID());
        s.setOrganisationId(ORG_ID);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartDate(start);
        s.setEndDate(end);
        return s;
    }
}
