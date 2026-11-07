package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionSource;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import com.diana.auditinsightbackendspringboot.Services.EntitlementService;
import com.diana.auditinsightbackendspringboot.Services.OrganisationLock;
import com.diana.auditinsightbackendspringboot.Services.PlatformFreeAccessService;
import com.diana.auditinsightbackendspringboot.Services.TrialGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganisationFreeAccessServiceTest {

    @Mock private OrganisationRepository organisationRepo;
    @Mock private SubscriptionRepository subscriptionRepo;
    @Mock private PaymentRepository paymentRepo;
    @Mock private TrialGrantRepository trialGrantRepo;
    @Mock private TrialGrantService trialGrantService;
    @Mock private PlatformFreeAccessService platformFreeAccessService;
    @Mock private UserRepository userRepo;
    @Mock private AdminAuditService adminAuditService;
    @Mock private OrganisationLock organisationLock;

    private OrganisationFreeAccessService service;
    private final UUID orgId = UUID.randomUUID();
    private static final String ADMIN = "admin@test.com";

    /** The org's ACTIVE rows as the repository would return them (mutated in place by the service). */
    private List<Subscription> rows;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        EntitlementService entitlementService = new EntitlementService(subscriptionRepo, trialGrantRepo, platformFreeAccessService);
        service = new OrganisationFreeAccessService(organisationRepo, subscriptionRepo, paymentRepo, trialGrantRepo,
                trialGrantService, entitlementService, userRepo, adminAuditService, organisationLock);

        Organisation org = new Organisation();
        org.setId(orgId);
        lenient().when(organisationRepo.findById(orgId)).thenReturn(Mono.just(org));
        User admin = new User();
        admin.setId(9L);
        lenient().when(userRepo.findByUsername(ADMIN)).thenReturn(Mono.just(admin));
        lenient().when(organisationLock.inTransaction(any(), any()))
                .thenAnswer(inv -> ((Supplier<Mono<?>>) inv.getArgument(1)).get());
        lenient().when(platformFreeAccessService.activeUntil()).thenReturn(Mono.just(Optional.empty()));
        lenient().when(trialGrantRepo.findFirstByOrganisationIdAndStatus(orgId, TrialGrantStatus.ACTIVE)).thenReturn(Mono.empty());
        lenient().when(subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(orgId, SubscriptionStatus.ACTIVE))
                .thenAnswer(inv -> Flux.fromIterable(rows.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).toList()));
        lenient().when(subscriptionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(adminAuditService.record(any(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.just(new AdminAuditLog()));
        rows = List.of();
    }

    @Test
    void endsCurrentComplimentaryPeriod_andPullsQueuedPaidTimeForward() {
        LocalDateTime now = LocalDateTime.now();
        Subscription free = period(now.minusDays(5), now.plusDays(25), SubscriptionSource.ADMIN_COMPLIMENTARY);
        Subscription paid = period(free.getEndDate(), free.getEndDate().plusDays(30), SubscriptionSource.PAYMENT);
        rows = List.of(free, paid);
        paidBy(paid, true);
        paidBy(free, false);

        LocalDateTime before = LocalDateTime.now();
        StepVerifier.create(service.endFreeAccess(orgId, "Ended the pilot", ADMIN))
                .assertNext(view -> assertThat(view.hasFreeAccess()).isFalse())
                .verifyComplete();

        assertThat(free.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(free.getAdminReason()).isEqualTo("Ended the pilot");
        assertThat(paid.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(paid.getStartDate()).isBetween(before.minusSeconds(1), LocalDateTime.now());
        assertThat(Duration.between(paid.getStartDate(), paid.getEndDate())).isEqualTo(Duration.ofDays(30));
        verify(adminAuditService).record(eq(9L), eq("END_ORGANISATION_FREE_ACCESS"), eq("ORGANISATION"),
                eq(orgId.toString()), any(), any(), eq("Ended the pilot"));
    }

    @Test
    void legacyRowWithoutPaymentIsFree_butPaidLegacyRowIsKept() {
        LocalDateTime now = LocalDateTime.now();
        Subscription legacyFree = period(now.minusDays(1), now.plusDays(10), SubscriptionSource.LEGACY);
        Subscription legacyPaid = period(now.minusDays(1), now.plusDays(10), SubscriptionSource.LEGACY);
        rows = List.of(legacyFree, legacyPaid);
        paidBy(legacyFree, false);
        paidBy(legacyPaid, true);

        StepVerifier.create(service.endFreeAccess(orgId, "No payment on record", ADMIN))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(legacyFree.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(legacyPaid.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void queuedFreePeriodIsCancelled() {
        LocalDateTime now = LocalDateTime.now();
        Subscription queuedFree = period(now.plusDays(3), now.plusDays(33), SubscriptionSource.ADMIN_COMPLIMENTARY);
        rows = List.of(queuedFree);
        paidBy(queuedFree, false);

        StepVerifier.create(service.endFreeAccess(orgId, "Withdrawn", ADMIN)).expectNextCount(1).verifyComplete();
        assertThat(queuedFree.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void runningTrialIsRevoked() {
        TrialGrant trial = new TrialGrant();
        trial.setStatus(TrialGrantStatus.ACTIVE);
        trial.setStartsAt(LocalDateTime.now().minusDays(1));
        trial.setEndsAt(LocalDateTime.now().plusDays(6));
        trial.setDurationDays(7);
        when(trialGrantRepo.findFirstByOrganisationIdAndStatus(orgId, TrialGrantStatus.ACTIVE)).thenReturn(Mono.just(trial));
        when(trialGrantService.revoke(orgId, "Misuse", ADMIN)).thenAnswer(inv -> {
            trial.setStatus(TrialGrantStatus.REVOKED);
            return Mono.just(trial);
        });

        StepVerifier.create(service.endFreeAccess(orgId, "Misuse", ADMIN)).expectNextCount(1).verifyComplete();
        verify(trialGrantService).revoke(orgId, "Misuse", ADMIN);
    }

    @Test
    void nothingFree_isRejected() {
        LocalDateTime now = LocalDateTime.now();
        Subscription paid = period(now.minusDays(1), now.plusDays(29), SubscriptionSource.PAYMENT);
        rows = List.of(paid);
        paidBy(paid, true);

        StepVerifier.create(service.endFreeAccess(orgId, "x", ADMIN))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("no free access"))
                .verify();
        verify(subscriptionRepo, never()).save(any());
    }

    @Test
    void reasonIsRequired() {
        StepVerifier.create(service.endFreeAccess(orgId, " ", ADMIN)).expectError(InvalidRecord.class).verify();
    }

    private void paidBy(Subscription s, boolean paid) {
        lenient().when(paymentRepo.existsBySubscriptionIdAndStatus(s.getId(), PaymentStatus.SUCCESSFUL)).thenReturn(Mono.just(paid));
    }

    private Subscription period(LocalDateTime start, LocalDateTime end, SubscriptionSource source) {
        Subscription s = new Subscription();
        s.setId(UUID.randomUUID());
        s.setOrganisationId(orgId);
        s.setSubscriptionType(SubscriptionType.MONTHLY);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setSource(source);
        s.setStartDate(start);
        s.setEndDate(end);
        return s;
    }
}
