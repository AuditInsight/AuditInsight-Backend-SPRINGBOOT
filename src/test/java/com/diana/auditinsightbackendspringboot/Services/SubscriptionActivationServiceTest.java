package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionSource;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionActivationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private TrialGrantRepository trialGrantRepository;
    @Mock private PlanPriceService planPriceService;
    @Mock private OrganisationLock organisationLock;

    private SubscriptionActivationService service;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new SubscriptionActivationService(paymentRepository, subscriptionRepository, trialGrantRepository,
                planPriceService, organisationLock);
        lenient().when(organisationLock.inTransaction(any(), any()))
                .thenAnswer(inv -> ((Supplier<Mono<?>>) inv.getArgument(1)).get());
        lenient().when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return Mono.just(s);
        });
        lenient().when(subscriptionRepository.findAllByOrganisationIdAndStatusOrderByEndDateAsc(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Flux.empty());
        lenient().when(trialGrantRepository.findFirstByOrganisationIdAndStatus(orgId, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.empty());
        lenient().when(planPriceService.getPrice(SubscriptionType.MONTHLY)).thenReturn(Mono.just(price(30)));
        lenient().when(paymentRepository.linkSubscriptionIfAbsent(any(), any())).thenReturn(Mono.just(1));
    }

    // ── payments ──────────────────────────────────────────────────────────────────

    @Test
    void firstPayment_startsPaidPeriodNow() {
        Payment payment = payment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));

        LocalDateTime before = LocalDateTime.now();
        StepVerifier.create(service.activateFromPayment(payment))
                .assertNext(s -> {
                    assertThat(s.getSource()).isEqualTo(SubscriptionSource.PAYMENT);
                    assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(s.getStartDate()).isBetween(before, LocalDateTime.now());
                    assertThat(Duration.between(s.getStartDate(), s.getEndDate())).isEqualTo(Duration.ofDays(30));
                })
                .verifyComplete();
        verify(paymentRepository).linkSubscriptionIfAbsent(any(), any());
    }

    @Test
    void renewingEarly_queuesNewPeriodAfterCurrentOne_withoutTouchingIt() {
        Subscription current = period(LocalDateTime.now().minusDays(20), LocalDateTime.now().plusDays(10));
        LocalDateTime currentEnd = current.getEndDate();
        when(subscriptionRepository.findAllByOrganisationIdAndStatusOrderByEndDateAsc(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Flux.just(current));
        Payment payment = payment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));

        StepVerifier.create(service.activateFromPayment(payment))
                .assertNext(s -> {
                    assertThat(s.getId()).isNotEqualTo(current.getId());
                    assertThat(s.getStartDate()).isEqualTo(currentEnd);
                    assertThat(s.getEndDate()).isEqualTo(currentEnd.plusDays(30));
                })
                .verifyComplete();
        assertThat(current.getEndDate()).isEqualTo(currentEnd);
    }

    @Test
    void payingDuringTrial_startsPaidTimeWhenTrialEnds() {
        TrialGrant trial = new TrialGrant();
        trial.setStatus(TrialGrantStatus.ACTIVE);
        trial.setStartsAt(LocalDateTime.now().minusDays(3));
        trial.setEndsAt(LocalDateTime.now().plusDays(11));
        when(trialGrantRepository.findFirstByOrganisationIdAndStatus(orgId, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.just(trial));
        Payment payment = payment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));

        StepVerifier.create(service.activateFromPayment(payment))
                .assertNext(s -> {
                    assertThat(s.getStartDate()).isEqualTo(trial.getEndsAt());
                    assertThat(s.getEndDate()).isEqualTo(trial.getEndsAt().plusDays(30));
                })
                .verifyComplete();
    }

    @Test
    void secondPaymentDuringTrial_queuesAfterTheFirstPaidPeriod() {
        TrialGrant trial = new TrialGrant();
        trial.setStatus(TrialGrantStatus.ACTIVE);
        trial.setStartsAt(LocalDateTime.now().minusDays(3));
        trial.setEndsAt(LocalDateTime.now().plusDays(11));
        when(trialGrantRepository.findFirstByOrganisationIdAndStatus(orgId, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.just(trial));
        Subscription queued = period(trial.getEndsAt(), trial.getEndsAt().plusDays(30));
        when(subscriptionRepository.findAllByOrganisationIdAndStatusOrderByEndDateAsc(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Flux.just(queued));
        Payment payment = payment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));

        StepVerifier.create(service.activateFromPayment(payment))
                .assertNext(s -> assertThat(s.getStartDate()).isEqualTo(queued.getEndDate()))
                .verifyComplete();
    }

    @Test
    void expiredTrialIsIgnored() {
        TrialGrant trial = new TrialGrant();
        trial.setStatus(TrialGrantStatus.ACTIVE);
        trial.setStartsAt(LocalDateTime.now().minusDays(10));
        trial.setEndsAt(LocalDateTime.now().minusDays(1));
        when(trialGrantRepository.findFirstByOrganisationIdAndStatus(orgId, TrialGrantStatus.ACTIVE))
                .thenReturn(Mono.just(trial));
        Payment payment = payment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));

        LocalDateTime before = LocalDateTime.now();
        StepVerifier.create(service.activateFromPayment(payment))
                .assertNext(s -> assertThat(s.getStartDate()).isBetween(before, LocalDateTime.now()))
                .verifyComplete();
    }

    @Test
    void alreadyLinkedPayment_returnsItsPeriodAndBuysNothing() {
        Payment payment = payment();
        Subscription linked = period(LocalDateTime.now(), LocalDateTime.now().plusDays(30));
        payment.setSubscriptionId(linked.getId());
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(subscriptionRepository.findById(linked.getId())).thenReturn(Mono.just(linked));

        StepVerifier.create(service.activateFromPayment(payment)).expectNext(linked).verifyComplete();
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void activationRunsUnderTheOrganisationLock() {
        Payment payment = payment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));

        StepVerifier.create(service.activateFromPayment(payment)).expectNextCount(1).verifyComplete();
        verify(organisationLock).inTransaction(org.mockito.ArgumentMatchers.eq(orgId), any());
    }

    // ── Super Admin changes ───────────────────────────────────────────────────────

    @Test
    void adminChange_withoutCurrentPeriod_createsComplimentaryPeriodWithReason() {
        StepVerifier.create(service.adminChangeSubscription(orgId, SubscriptionType.MONTHLY, null, 5L, "Partner"))
                .assertNext(s -> {
                    assertThat(s.getSource()).isEqualTo(SubscriptionSource.ADMIN_COMPLIMENTARY);
                    assertThat(s.getAdminReason()).isEqualTo("Partner");
                    assertThat(s.getCreatedBy()).isEqualTo(5L);
                    assertThat(Duration.between(s.getStartDate(), s.getEndDate())).isEqualTo(Duration.ofDays(30));
                })
                .verifyComplete();
    }

    @Test
    void adminChange_onCurrentPeriod_changesItAndMovesQueuedPeriods() {
        Subscription current = period(LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(25));
        LocalDateTime oldEnd = current.getEndDate();
        Subscription queued = period(oldEnd, oldEnd.plusDays(30));
        when(subscriptionRepository.findAllByOrganisationIdAndStatusOrderByEndDateAsc(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Flux.just(current, queued));

        LocalDateTime newEnd = oldEnd.plusDays(10);
        StepVerifier.create(service.adminChangeSubscription(orgId, SubscriptionType.MONTHLY, newEnd, 5L, "Goodwill"))
                .assertNext(s -> {
                    assertThat(s.getId()).isEqualTo(current.getId());
                    assertThat(s.getEndDate()).isEqualTo(newEnd);
                    assertThat(s.getAdminReason()).isEqualTo("Goodwill");
                })
                .verifyComplete();
        assertThat(queued.getStartDate()).isEqualTo(newEnd);
        assertThat(queued.getEndDate()).isEqualTo(newEnd.plusDays(30));
    }

    @Test
    void adminChange_rejectsPastEndDate() {
        StepVerifier.create(service.adminChangeSubscription(orgId, SubscriptionType.MONTHLY,
                        LocalDateTime.now().minusDays(1), 5L, null))
                .expectError(InvalidRecord.class)
                .verify();
    }

    @Test
    void periodsAreSavedAsNewRows() {
        Payment payment = payment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));

        StepVerifier.create(service.activateFromPayment(payment)).expectNextCount(1).verifyComplete();
        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getOrganisationId()).isEqualTo(orgId);
        assertThat(saved.getValue().getSubscriptionType()).isEqualTo(SubscriptionType.MONTHLY);
    }

    private Payment payment() {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrganisationId(orgId);
        p.setSubscriptionType(SubscriptionType.MONTHLY);
        p.setStatus(PaymentStatus.SUCCESSFUL);
        p.setCreatedBy(1L);
        return p;
    }

    private Subscription period(LocalDateTime start, LocalDateTime end) {
        Subscription s = new Subscription();
        s.setId(UUID.randomUUID());
        s.setOrganisationId(orgId);
        s.setSubscriptionType(SubscriptionType.MONTHLY);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartDate(start);
        s.setEndDate(end);
        return s;
    }

    private PlanPrice price(int days) {
        PlanPrice p = new PlanPrice();
        p.setSubscriptionType(SubscriptionType.MONTHLY);
        p.setDurationDays(days);
        return p;
    }
}
