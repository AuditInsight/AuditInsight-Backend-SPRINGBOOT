package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionActivationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanPriceService planPriceService;

    private SubscriptionActivationService activationService;

    private void init() {
        activationService = new SubscriptionActivationService(paymentRepository, subscriptionRepository, planPriceService);
    }

    private PlanPrice planPrice(SubscriptionType type, String price, int durationDays) {
        PlanPrice p = new PlanPrice();
        p.setSubscriptionType(type);
        p.setPrice(new BigDecimal(price));
        p.setCurrency("RWF");
        p.setDurationDays(durationDays);
        return p;
    }

    private Payment payment(UUID orgId, SubscriptionType type) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrganisationId(orgId);
        p.setSubscriptionType(type);
        p.setCreatedBy(1L);
        return p;
    }

    @Test
    void activateFromPayment_noExistingSubscription_createsNewActiveOneWithMonthlyEndDate() {
        init();
        UUID orgId = UUID.randomUUID();
        Payment payment = payment(orgId, SubscriptionType.MONTHLY);

        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(subscriptionRepository.findByOrganisationIdAndStatus(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Mono.empty());
        when(planPriceService.getPrice(SubscriptionType.MONTHLY))
                .thenReturn(Mono.just(planPrice(SubscriptionType.MONTHLY, "15000", 30)));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(captor.capture())).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return Mono.just(s);
        });
        when(paymentRepository.linkSubscriptionIfAbsent(eq(payment.getId()), any())).thenReturn(Mono.just(1));

        StepVerifier.create(activationService.activateFromPayment(payment))
                .expectNextMatches(s -> s.getStatus() == SubscriptionStatus.ACTIVE
                        && s.getSubscriptionType() == SubscriptionType.MONTHLY
                        && ChronoUnit.DAYS.between(s.getStartDate(), s.getEndDate()) == 30)
                .verifyComplete();

        verify(subscriptionRepository, times(1)).save(any());
    }

    @Test
    void activateFromPayment_noExistingSubscription_annualCycleAdds365Days() {
        init();
        UUID orgId = UUID.randomUUID();
        Payment payment = payment(orgId, SubscriptionType.ANNUAL);

        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(subscriptionRepository.findByOrganisationIdAndStatus(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Mono.empty());
        when(planPriceService.getPrice(SubscriptionType.ANNUAL))
                .thenReturn(Mono.just(planPrice(SubscriptionType.ANNUAL, "150000", 365)));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return Mono.just(s);
        });
        when(paymentRepository.linkSubscriptionIfAbsent(eq(payment.getId()), any())).thenReturn(Mono.just(1));

        StepVerifier.create(activationService.activateFromPayment(payment))
                .expectNextMatches(s -> s.getEndDate().equals(s.getStartDate().plusDays(365)))
                .verifyComplete();
    }

    @Test
    void activateFromPayment_existingActiveSubscriptionSamePlan_extendsEndDateInsteadOfCreating() {
        init();
        UUID orgId = UUID.randomUUID();
        Payment payment = payment(orgId, SubscriptionType.MONTHLY);

        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setOrganisationId(orgId);
        existing.setSubscriptionType(SubscriptionType.MONTHLY);
        existing.setStatus(SubscriptionStatus.ACTIVE);
        LocalDateTime currentEnd = LocalDateTime.now().plusDays(10);
        existing.setEndDate(currentEnd);

        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(subscriptionRepository.findByOrganisationIdAndStatus(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Mono.just(existing));
        when(planPriceService.getPrice(SubscriptionType.MONTHLY))
                .thenReturn(Mono.just(planPrice(SubscriptionType.MONTHLY, "15000", 30)));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentRepository.linkSubscriptionIfAbsent(eq(payment.getId()), eq(existing.getId())))
                .thenReturn(Mono.just(1));

        StepVerifier.create(activationService.activateFromPayment(payment))
                .expectNextMatches(s -> s.getId().equals(existing.getId())
                        && s.getEndDate().equals(currentEnd.plusDays(30)))
                .verifyComplete();

        verify(subscriptionRepository, never()).findById(any(UUID.class));
    }

    @Test
    void activateFromPayment_alreadyLinkedPayment_isIdempotentAndDoesNotSaveAgain() {
        init();
        UUID orgId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Payment payment = payment(orgId, SubscriptionType.MONTHLY);
        payment.setSubscriptionId(subscriptionId);

        Subscription existing = new Subscription();
        existing.setId(subscriptionId);
        existing.setStatus(SubscriptionStatus.ACTIVE);

        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Mono.just(existing));

        // Call twice — as a duplicate webhook delivery would.
        StepVerifier.create(activationService.activateFromPayment(payment))
                .expectNextMatches(s -> s.getId().equals(subscriptionId))
                .verifyComplete();
        StepVerifier.create(activationService.activateFromPayment(payment))
                .expectNextMatches(s -> s.getId().equals(subscriptionId))
                .verifyComplete();

        verify(subscriptionRepository, never()).save(any());
        verify(paymentRepository, never()).linkSubscriptionIfAbsent(any(), any());
    }

    @Test
    void activateFromPayment_concurrentLinkLoses_returnsWhicheverSubscriptionWon() {
        init();
        UUID orgId = UUID.randomUUID();
        Payment payment = payment(orgId, SubscriptionType.MONTHLY);

        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(subscriptionRepository.findByOrganisationIdAndStatus(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Mono.empty());
        when(planPriceService.getPrice(SubscriptionType.MONTHLY))
                .thenReturn(Mono.just(planPrice(SubscriptionType.MONTHLY, "15000", 30)));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return Mono.just(s);
        });
        // Lost the race: 0 rows updated.
        when(paymentRepository.linkSubscriptionIfAbsent(eq(payment.getId()), any())).thenReturn(Mono.just(0));

        UUID winnerSubId = UUID.randomUUID();
        Payment relinkedPayment = payment(orgId, SubscriptionType.MONTHLY);
        relinkedPayment.setId(payment.getId());
        relinkedPayment.setSubscriptionId(winnerSubId);
        Subscription winner = new Subscription();
        winner.setId(winnerSubId);

        when(paymentRepository.findById(payment.getId()))
                .thenReturn(Mono.just(payment), Mono.just(relinkedPayment));
        when(subscriptionRepository.findById(winnerSubId)).thenReturn(Mono.just(winner));

        StepVerifier.create(activationService.activateFromPayment(payment))
                .expectNextMatches(s -> s.getId().equals(winnerSubId))
                .verifyComplete();
    }

    // ──────────────────────────── adminChangeSubscription ────────────────────────────

    @Test
    void adminChangeSubscription_noActiveSubscription_insertsFirstActiveRow() {
        init();
        UUID orgId = UUID.randomUUID();
        when(subscriptionRepository.findByOrganisationIdAndStatus(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Mono.empty());
        when(planPriceService.getPrice(SubscriptionType.ANNUAL))
                .thenReturn(Mono.just(planPrice(SubscriptionType.ANNUAL, "150000", 365)));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return Mono.just(s);
        });

        StepVerifier.create(activationService.adminChangeSubscription(orgId, SubscriptionType.ANNUAL, null, 42L))
                .expectNextMatches(s -> s.getStatus() == SubscriptionStatus.ACTIVE
                        && s.getSubscriptionType() == SubscriptionType.ANNUAL
                        && s.getCreatedBy().equals(42L)
                        && ChronoUnit.DAYS.between(s.getStartDate(), s.getEndDate()) == 365)
                .verifyComplete();

        verify(subscriptionRepository, times(1)).save(any());
    }

    @Test
    void adminChangeSubscription_existingActiveSubscription_updatesInPlaceRatherThanInserting() {
        init();
        UUID orgId = UUID.randomUUID();
        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setOrganisationId(orgId);
        existing.setSubscriptionType(SubscriptionType.MONTHLY);
        existing.setStatus(SubscriptionStatus.ACTIVE);
        existing.setStartDate(LocalDateTime.now().minusDays(5));
        existing.setEndDate(LocalDateTime.now().plusDays(25));

        when(subscriptionRepository.findByOrganisationIdAndStatus(orgId, SubscriptionStatus.ACTIVE))
                .thenReturn(Mono.just(existing));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        LocalDateTime explicitEndDate = LocalDateTime.now().plusDays(180);

        StepVerifier.create(activationService.adminChangeSubscription(orgId, SubscriptionType.SIX_MONTHS, explicitEndDate, 42L))
                .assertNext(s -> org.assertj.core.api.Assertions.assertThat(s.getId()).isEqualTo(existing.getId()))
                .verifyComplete();

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(1)).save(captor.capture());
        Subscription saved = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(saved.getId()).isEqualTo(existing.getId());
        org.assertj.core.api.Assertions.assertThat(saved.getSubscriptionType()).isEqualTo(SubscriptionType.SIX_MONTHS);
        org.assertj.core.api.Assertions.assertThat(saved.getEndDate()).isEqualTo(explicitEndDate);
        verify(planPriceService, never()).getPrice(any());
    }
}
