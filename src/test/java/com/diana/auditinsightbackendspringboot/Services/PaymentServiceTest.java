package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.*;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.*;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganisationMemberRepository memberRepository;
    @Mock private PawaPayService pawaPayService;
    @Mock private FlutterwaveService flutterwaveService;
    @Mock private SubscriptionActivationService activationService;
    @Mock private PlanPriceService planPriceService;

    private PaymentService paymentService;

    private final UUID orgId = UUID.randomUUID();
    private final String email = "client@test.com";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, subscriptionRepository, userRepository,
                memberRepository, pawaPayService, flutterwaveService, activationService, planPriceService);
    }

    private PlanPrice monthlyPlanPrice() {
        PlanPrice p = new PlanPrice();
        p.setSubscriptionType(SubscriptionType.MONTHLY);
        p.setPrice(new BigDecimal("15000"));
        p.setCurrency("RWF");
        p.setDurationDays(30);
        return p;
    }

    private User user() {
        User u = new User();
        u.setId(1L);
        u.setUsername(email);
        u.setRole(Role.CLIENT);
        return u;
    }

    private OrganisationMember activeMember() {
        OrganisationMember m = new OrganisationMember();
        m.setOrganisationId(orgId);
        m.setUserId(1L);
        m.setStatus(MemberStatus.ACTIVE);
        m.setRole(Role.CLIENT);
        return m;
    }

    // ──────────────────────────── startMomoCheckout ────────────────────────────

    @Test
    void startMomoCheckout_happyPath_createsPendingPaymentAndCallsMomo() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(planPriceService.getPrice(SubscriptionType.MONTHLY)).thenReturn(Mono.just(monthlyPlanPrice()));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return Mono.just(p);
        });
        when(pawaPayService.requestDeposit(any(), any(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.startMomoCheckout(orgId, email, SubscriptionType.MONTHLY, "0788123456"))
                .expectNextMatches(p -> p.getStatus() == PaymentStatus.PENDING
                        && p.getProvider() == PaymentProvider.MOMO
                        && p.getCurrency().equals("RWF")
                        && p.getExpectedAmount().compareTo(new BigDecimal("15000")) == 0)
                .verifyComplete();

        verify(pawaPayService, times(1)).requestDeposit(any(), any(), eq("0788123456"));
    }

    @Test
    void startMomoCheckout_momoRequestFails_marksPaymentFailedAndPropagatesError() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(planPriceService.getPrice(SubscriptionType.MONTHLY)).thenReturn(Mono.just(monthlyPlanPrice()));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return Mono.just(p);
        });
        when(pawaPayService.requestDeposit(any(), any(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("MTN sandbox unreachable")));

        StepVerifier.create(paymentService.startMomoCheckout(orgId, email, SubscriptionType.MONTHLY, "0788123456"))
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("unreachable"))
                .verify();

        verify(paymentRepository, times(2)).save(any()); // initial PENDING save + FAILED save
        verify(activationService, never()).activateFromPayment(any());
    }

    @Test
    void startMomoCheckout_nonMember_emitsForbidden() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.startMomoCheckout(orgId, email, SubscriptionType.MONTHLY, "0788123456"))
                .expectError(ForbiddenException.class)
                .verify();
    }

    // ──────────────────────────── getMomoPaymentStatus ────────────────────────────

    private Payment pendingMomoPayment() {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrganisationId(orgId);
        p.setProvider(PaymentProvider.MOMO);
        p.setStatus(PaymentStatus.PENDING);
        p.setSubscriptionType(SubscriptionType.MONTHLY);
        p.setExpectedAmount(SubscriptionType.MONTHLY.getPriceRwf());
        p.setCurrency("RWF");
        return p;
    }

    @Test
    void getMomoPaymentStatus_momoReportsSuccessfulWithFullAmount_marksSuccessfulAndActivatesSubscription() {
        Payment payment = pendingMomoPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(pawaPayService.getStatus(payment.getId())).thenReturn(Mono.just(
                new PawaPayService.StatusResult(PawaPayService.PawaPayStatus.SUCCESSFUL, null, new BigDecimal("15000"))));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        when(activationService.activateFromPayment(any())).thenReturn(Mono.just(sub));

        StepVerifier.create(paymentService.getMomoPaymentStatus(payment.getId(), email))
                .expectNextMatches(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .verifyComplete();

        verify(activationService, times(1)).activateFromPayment(any());
    }

    @Test
    void getMomoPaymentStatus_momoReportsSuccessfulButUnderpaid_marksUnderpaidWithoutActivating() {
        Payment payment = pendingMomoPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(pawaPayService.getStatus(payment.getId())).thenReturn(Mono.just(
                new PawaPayService.StatusResult(PawaPayService.PawaPayStatus.SUCCESSFUL, null, new BigDecimal("10000"))));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(paymentService.getMomoPaymentStatus(payment.getId(), email))
                .expectNextMatches(p -> p.getStatus() == PaymentStatus.UNDERPAID
                        && p.getReceivedAmount().compareTo(new BigDecimal("10000")) == 0)
                .verifyComplete();

        verify(activationService, never()).activateFromPayment(any());
    }

    @Test
    void getMomoPaymentStatus_momoReportsFailed_marksFailedAndLeavesSubscriptionUntouched() {
        Payment payment = pendingMomoPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(pawaPayService.getStatus(payment.getId())).thenReturn(Mono.just(
                new PawaPayService.StatusResult(PawaPayService.PawaPayStatus.FAILED, "PAYER_NOT_FOUND: Payer not found", null)));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(paymentService.getMomoPaymentStatus(payment.getId(), email))
                .expectNextMatches(p -> p.getStatus() == PaymentStatus.FAILED
                        && "PAYER_NOT_FOUND: Payer not found".equals(p.getFailureReason()))
                .verifyComplete();

        verify(activationService, never()).activateFromPayment(any());
    }

    @Test
    void getMomoPaymentStatus_stillPending_returnsPendingWithoutSideEffects() {
        Payment payment = pendingMomoPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(pawaPayService.getStatus(payment.getId())).thenReturn(Mono.just(
                new PawaPayService.StatusResult(PawaPayService.PawaPayStatus.PENDING, null, null)));

        StepVerifier.create(paymentService.getMomoPaymentStatus(payment.getId(), email))
                .expectNextMatches(p -> p.getStatus() == PaymentStatus.PENDING)
                .verifyComplete();

        verify(paymentRepository, never()).save(any());
        verify(activationService, never()).activateFromPayment(any());
    }

    @Test
    void getMomoPaymentStatus_alreadyTerminal_doesNotRePollMomo() {
        Payment payment = pendingMomoPayment();
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        when(paymentRepository.findById(payment.getId())).thenReturn(Mono.just(payment));
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));

        StepVerifier.create(paymentService.getMomoPaymentStatus(payment.getId(), email))
                .expectNextMatches(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .verifyComplete();

        verify(pawaPayService, never()).getStatus(any());
    }

    // ──────────────────────────── startCardCheckout ────────────────────────────

    @Test
    void startCardCheckout_happyPath_createsPendingRwfPaymentAndReturnsCheckoutUrl() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(planPriceService.getPrice(SubscriptionType.MONTHLY)).thenReturn(Mono.just(monthlyPlanPrice()));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return Mono.just(p);
        });
        when(flutterwaveService.initiateCheckout(anyString(), any(), eq("RWF"), eq(email)))
                .thenReturn(Mono.just("https://checkout.flutterwave.com/pay/abc123"));

        StepVerifier.create(paymentService.startCardCheckout(orgId, email, SubscriptionType.MONTHLY))
                .expectNextMatches(result -> result.checkoutUrl().equals("https://checkout.flutterwave.com/pay/abc123")
                        && result.payment().getProvider() == PaymentProvider.CARD
                        && result.payment().getCurrency().equals("RWF")
                        && result.payment().getExpectedAmount().compareTo(new BigDecimal("15000")) == 0
                        && result.payment().getProviderTransactionId() != null)
                .verifyComplete();
    }

    @Test
    void startCardCheckout_flutterwaveInitiationFails_marksPaymentFailed() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(planPriceService.getPrice(SubscriptionType.MONTHLY)).thenReturn(Mono.just(monthlyPlanPrice()));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return Mono.just(p);
        });
        when(flutterwaveService.initiateCheckout(anyString(), any(), eq("RWF"), eq(email)))
                .thenReturn(Mono.error(new RuntimeException("Flutterwave sandbox unreachable")));

        StepVerifier.create(paymentService.startCardCheckout(orgId, email, SubscriptionType.MONTHLY))
                .expectErrorMatches(e -> e.getMessage().contains("unreachable"))
                .verify();

        verify(paymentRepository, times(2)).save(any());
    }

    // ──────────────────────────── handleFlutterwaveWebhook ────────────────────────────

    private Map<String, Object> webhookPayload(String txRef, String transactionId) {
        return Map.of("event", "charge.completed", "data", Map.of("tx_ref", txRef, "id", transactionId));
    }

    @Test
    void handleFlutterwaveWebhook_invalidSignature_rejectsWithoutVerifyingTransaction() {
        when(flutterwaveService.isValidWebhookSignature("bad-hash")).thenReturn(false);

        StepVerifier.create(paymentService.handleFlutterwaveWebhook("bad-hash", webhookPayload("AI-1", "999")))
                .expectError(ForbiddenException.class)
                .verify();

        verify(flutterwaveService, never()).verifyTransaction(any());
        verify(paymentRepository, never()).findByProviderTransactionId(any());
    }

    @Test
    void handleFlutterwaveWebhook_verifiedSuccessful_marksSuccessfulAndActivates() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrganisationId(orgId);
        payment.setProvider(PaymentProvider.CARD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderTransactionId("AI-1");
        payment.setExpectedAmount(new BigDecimal("15000"));
        payment.setCurrency("RWF");

        when(flutterwaveService.isValidWebhookSignature("good-hash")).thenReturn(true);
        when(paymentRepository.findByProviderTransactionId("AI-1")).thenReturn(Mono.just(payment));
        when(flutterwaveService.verifyTransaction("999")).thenReturn(Mono.just(
                new FlutterwaveService.VerificationResult(true, "successful", "AI-1", new BigDecimal("15000"), "RWF")));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        when(activationService.activateFromPayment(any())).thenReturn(Mono.just(sub));

        StepVerifier.create(paymentService.handleFlutterwaveWebhook("good-hash", webhookPayload("AI-1", "999")))
                .verifyComplete();

        verify(activationService, times(1)).activateFromPayment(any());
    }

    @Test
    void handleFlutterwaveWebhook_verifiedButUnderpaid_marksUnderpaidWithoutActivating() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrganisationId(orgId);
        payment.setProvider(PaymentProvider.CARD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderTransactionId("AI-1");
        payment.setExpectedAmount(new BigDecimal("15000"));
        payment.setCurrency("RWF");

        when(flutterwaveService.isValidWebhookSignature("good-hash")).thenReturn(true);
        when(paymentRepository.findByProviderTransactionId("AI-1")).thenReturn(Mono.just(payment));
        when(flutterwaveService.verifyTransaction("999")).thenReturn(Mono.just(
                new FlutterwaveService.VerificationResult(true, "successful", "AI-1", new BigDecimal("10000"), "RWF")));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(paymentService.handleFlutterwaveWebhook("good-hash", webhookPayload("AI-1", "999")))
                .verifyComplete();

        verify(activationService, never()).activateFromPayment(any());
        org.assertj.core.api.Assertions.assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNDERPAID);
    }

    @Test
    void handleFlutterwaveWebhook_verifiedTransactionBelongsToDifferentPayment_marksFailedWithoutActivating() {
        // Forged webhook: tx_ref points at this payment, but the Flutterwave transaction id in the
        // payload actually belongs to a different (genuinely successful) transaction/currency.
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrganisationId(orgId);
        payment.setProvider(PaymentProvider.CARD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderTransactionId("AI-1");
        payment.setExpectedAmount(new BigDecimal("15000"));
        payment.setCurrency("RWF");

        when(flutterwaveService.isValidWebhookSignature("good-hash")).thenReturn(true);
        when(paymentRepository.findByProviderTransactionId("AI-1")).thenReturn(Mono.just(payment));
        when(flutterwaveService.verifyTransaction("999")).thenReturn(Mono.just(
                new FlutterwaveService.VerificationResult(true, "successful", "SOMEONE-ELSES-REF", new BigDecimal("1.00"), "USD")));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(paymentService.handleFlutterwaveWebhook("good-hash", webhookPayload("AI-1", "999")))
                .verifyComplete();

        verify(activationService, never()).activateFromPayment(any());
        org.assertj.core.api.Assertions.assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void handleFlutterwaveWebhook_verifiedFailed_marksFailedWithoutActivating() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrganisationId(orgId);
        payment.setProvider(PaymentProvider.CARD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderTransactionId("AI-1");

        when(flutterwaveService.isValidWebhookSignature("good-hash")).thenReturn(true);
        when(paymentRepository.findByProviderTransactionId("AI-1")).thenReturn(Mono.just(payment));
        when(flutterwaveService.verifyTransaction("999")).thenReturn(Mono.just(
                new FlutterwaveService.VerificationResult(false, "failed", "AI-1", null, null)));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(paymentService.handleFlutterwaveWebhook("good-hash", webhookPayload("AI-1", "999")))
                .verifyComplete();

        verify(activationService, never()).activateFromPayment(any());
    }

    @Test
    void handleFlutterwaveWebhook_duplicateDeliveryForAlreadySuccessfulPayment_isIdempotentNoOp() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrganisationId(orgId);
        payment.setProvider(PaymentProvider.CARD);
        payment.setStatus(PaymentStatus.SUCCESSFUL); // already processed by a first delivery
        payment.setProviderTransactionId("AI-1");

        when(flutterwaveService.isValidWebhookSignature("good-hash")).thenReturn(true);
        when(paymentRepository.findByProviderTransactionId("AI-1")).thenReturn(Mono.just(payment));

        StepVerifier.create(paymentService.handleFlutterwaveWebhook("good-hash", webhookPayload("AI-1", "999")))
                .verifyComplete();

        verify(flutterwaveService, never()).verifyTransaction(any());
        verify(activationService, never()).activateFromPayment(any());
        verify(paymentRepository, never()).save(any());
    }

    // ──────────────────────────── getActiveSubscription ────────────────────────────

    @Test
    void getActiveSubscription_memberWithActivePaidSubscription_returnsIt() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        sub.setOrganisationId(orgId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findFirstByOrganisationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Mono.just(sub));

        StepVerifier.create(paymentService.getActiveSubscription(orgId, email))
                .expectNextMatches(s -> s.getId().equals(sub.getId()))
                .verifyComplete();
    }

    @Test
    void getActiveSubscription_memberOnTrial_returnsIt() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        Subscription trial = new Subscription();
        trial.setId(UUID.randomUUID());
        trial.setOrganisationId(orgId);
        trial.setStatus(SubscriptionStatus.TRIAL);
        when(subscriptionRepository.findFirstByOrganisationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Mono.just(trial));

        StepVerifier.create(paymentService.getActiveSubscription(orgId, email))
                .expectNextMatches(s -> s.getId().equals(trial.getId()))
                .verifyComplete();
    }

    @Test
    void getActiveSubscription_expired_emitsInvalidRecord() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        Subscription expired = new Subscription();
        expired.setId(UUID.randomUUID());
        expired.setOrganisationId(orgId);
        expired.setStatus(SubscriptionStatus.EXPIRED);
        when(subscriptionRepository.findFirstByOrganisationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Mono.just(expired));

        StepVerifier.create(paymentService.getActiveSubscription(orgId, email))
                .expectError(InvalidRecord.class)
                .verify();
    }

    @Test
    void getActiveSubscription_none_emitsInvalidRecord() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.just(activeMember()));
        when(subscriptionRepository.findFirstByOrganisationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getActiveSubscription(orgId, email))
                .expectError(InvalidRecord.class)
                .verify();
    }

    @Test
    void getActiveSubscription_nonMember_emitsForbidden() {
        when(userRepository.findByUsername(email)).thenReturn(Mono.just(user()));
        when(memberRepository.findByOrganisationIdAndUserId(orgId, 1L)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getActiveSubscription(orgId, email))
                .expectError(ForbiddenException.class)
                .verify();
    }
}
