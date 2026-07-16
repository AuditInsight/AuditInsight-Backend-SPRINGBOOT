package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.BillingCycle;
import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.Plan;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PlanRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final OrganisationMemberRepository memberRepository;
    private final ExchangeRateService exchangeRateService;
    private final PawaPayService pawaPayService;
    private final FlutterwaveService flutterwaveService;
    private final SubscriptionActivationService activationService;

    public PaymentService(PaymentRepository paymentRepository,
                           PlanRepository planRepository,
                           SubscriptionRepository subscriptionRepository,
                           UserRepository userRepository,
                           OrganisationMemberRepository memberRepository,
                           ExchangeRateService exchangeRateService,
                           PawaPayService pawaPayService,
                           FlutterwaveService flutterwaveService,
                           SubscriptionActivationService activationService) {
        this.paymentRepository = paymentRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.exchangeRateService = exchangeRateService;
        this.pawaPayService = pawaPayService;
        this.flutterwaveService = flutterwaveService;
        this.activationService = activationService;
    }

    public record CardCheckoutResult(Payment payment, String checkoutUrl) {}

    private Mono<User> resolveMember(UUID organisationId, String email) {
        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMap(user -> memberRepository.findByOrganisationIdAndUserId(organisationId, user.getId())
                        .switchIfEmpty(Mono.error(new ForbiddenException("You are not a member of this organisation")))
                        .flatMap(member -> member.getStatus() != MemberStatus.ACTIVE
                                ? Mono.error(new ForbiddenException("Your membership is not active"))
                                : Mono.just(user)));
    }

    public Mono<Payment> startMomoCheckout(UUID organisationId, String email, PlanTier planTier,
                                            BillingCycle billingCycle, String phoneNumber) {
        return resolveMember(organisationId, email)
                .flatMap(user -> planRepository.findById(planTier)
                        .switchIfEmpty(Mono.error(new InvalidRecord("Unknown plan: " + planTier)))
                        .flatMap(plan -> exchangeRateService.convert(plan.priceFor(billingCycle), "RWF")
                                .flatMap(conversion -> createPendingPayment(
                                        organisationId, planTier, billingCycle, PaymentProvider.MOMO,
                                        plan, conversion, phoneNumber, null, user.getId()))
                                .flatMap(payment -> pawaPayService.requestDeposit(
                                                payment.getId(), payment.getChargedAmount(), phoneNumber)
                                        .thenReturn(payment)
                                        .onErrorResume(e -> markFailed(payment).then(Mono.error(e))))));
    }

    public Mono<CardCheckoutResult> startCardCheckout(UUID organisationId, String email, PlanTier planTier,
                                                        BillingCycle billingCycle) {
        return resolveMember(organisationId, email)
                .flatMap(user -> planRepository.findById(planTier)
                        .switchIfEmpty(Mono.error(new InvalidRecord("Unknown plan: " + planTier)))
                        .flatMap(plan -> {
                            BigDecimal usd = plan.priceFor(billingCycle);
                            // Flutterwave supports charging directly in USD, so no second currency
                            // conversion is introduced for the card path — rate is locked at 1.
                            ExchangeRateService.ConversionResult directUsd =
                                    new ExchangeRateService.ConversionResult(usd, "USD", usd, BigDecimal.ONE);
                            String txRef = "AI-" + UUID.randomUUID();

                            return createPendingPayment(organisationId, planTier, billingCycle, PaymentProvider.CARD,
                                    plan, directUsd, null, txRef, user.getId())
                                    .flatMap(payment -> flutterwaveService.initiateCheckout(txRef, usd, "USD", email)
                                            .map(checkoutUrl -> new CardCheckoutResult(payment, checkoutUrl))
                                            .onErrorResume(e -> markFailed(payment).then(Mono.error(e))));
                        }));
    }

    private Mono<Payment> createPendingPayment(UUID organisationId, PlanTier planTier, BillingCycle billingCycle,
                                                PaymentProvider provider, Plan plan,
                                                ExchangeRateService.ConversionResult conversion,
                                                String payerPhone, String providerReference, Long createdBy) {
        Payment payment = new Payment();
        payment.setOrganisationId(organisationId);
        payment.setPlanTier(planTier);
        payment.setBillingCycle(billingCycle);
        payment.setProvider(provider);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUsdAmount(plan.priceFor(billingCycle));
        payment.setExchangeRate(conversion.rate());
        payment.setChargedCurrency(conversion.currency());
        payment.setChargedAmount(conversion.convertedAmount());
        payment.setPayerPhone(payerPhone);
        payment.setProviderReference(providerReference);
        payment.setCreatedBy(createdBy);
        LocalDateTime now = LocalDateTime.now();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        return paymentRepository.save(payment);
    }

    private Mono<Payment> markFailed(Payment payment) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    private Mono<Payment> markSuccessfulAndActivate(Payment payment) {
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(payment)
                .flatMap(saved -> activationService.activateFromPayment(saved).thenReturn(saved));
    }

    public Mono<Payment> getMomoPaymentStatus(UUID paymentId, String email) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Payment not found")))
                .flatMap(payment -> resolveMember(payment.getOrganisationId(), email).thenReturn(payment))
                .flatMap(payment -> {
                    if (payment.getStatus() != PaymentStatus.PENDING) {
                        return Mono.just(payment);
                    }
                    return pawaPayService.getStatus(payment.getId())
                            .flatMap(status -> switch (status) {
                                case SUCCESSFUL -> markSuccessfulAndActivate(payment);
                                case FAILED -> markFailed(payment);
                                case PENDING -> Mono.just(payment);
                            });
                });
    }

    @SuppressWarnings("unchecked")
    public Mono<Void> handleFlutterwaveWebhook(String verifHashHeader, Map<String, Object> payload) {
        if (!flutterwaveService.isValidWebhookSignature(verifHashHeader)) {
            return Mono.error(new ForbiddenException("Invalid webhook signature"));
        }

        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map)) {
            return Mono.error(new InvalidRecord("Malformed Flutterwave webhook payload"));
        }
        Map<String, Object> data = (Map<String, Object>) dataObj;
        if (data.get("tx_ref") == null || data.get("id") == null) {
            return Mono.error(new InvalidRecord("Malformed Flutterwave webhook payload"));
        }

        String txRef = String.valueOf(data.get("tx_ref"));
        String transactionId = String.valueOf(data.get("id"));

        return paymentRepository.findByProviderReference(txRef)
                .switchIfEmpty(Mono.error(new InvalidRecord("Unknown payment reference: " + txRef)))
                .flatMap(payment -> {
                    // Already terminal — duplicate webhook delivery, safely ignored.
                    if (payment.getStatus() != PaymentStatus.PENDING) {
                        return Mono.empty();
                    }
                    return flutterwaveService.verifyTransaction(transactionId)
                            .flatMap(verification -> verification.successful()
                                    ? markSuccessfulAndActivate(payment).then()
                                    : markFailed(payment).then());
                });
    }

    public Mono<Subscription> getActiveSubscription(UUID organisationId, String email) {
        return resolveMember(organisationId, email)
                .then(Mono.defer(() -> subscriptionRepository
                        .findByOrganisationIdAndStatus(organisationId, SubscriptionStatus.ACTIVE)))
                .switchIfEmpty(Mono.error(new InvalidRecord("No active subscription for this organisation")));
    }
}
