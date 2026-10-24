package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
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
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final OrganisationMemberRepository memberRepository;
    private final PawaPayService pawaPayService;
    private final FlutterwaveService flutterwaveService;
    private final SubscriptionActivationService activationService;
    private final PlanPriceService planPriceService;

    public PaymentService(PaymentRepository paymentRepository,
                           SubscriptionRepository subscriptionRepository,
                           UserRepository userRepository,
                           OrganisationMemberRepository memberRepository,
                           PawaPayService pawaPayService,
                           FlutterwaveService flutterwaveService,
                           SubscriptionActivationService activationService,
                           PlanPriceService planPriceService) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.pawaPayService = pawaPayService;
        this.flutterwaveService = flutterwaveService;
        this.activationService = activationService;
        this.planPriceService = planPriceService;
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

    public Mono<Payment> startMomoCheckout(UUID organisationId, String email, SubscriptionType subscriptionType,
                                            String phoneNumber) {
        return resolveMember(organisationId, email)
                .flatMap(user -> createPendingPayment(organisationId, subscriptionType, PaymentProvider.MOMO,
                                phoneNumber, null, user.getId())
                        .flatMap(payment -> pawaPayService.requestDeposit(
                                        payment.getId(), payment.getExpectedAmount(), phoneNumber)
                                .thenReturn(payment)
                                .onErrorResume(e -> markFailed(payment, e.getMessage()).then(Mono.error(e)))));
    }

    public Mono<CardCheckoutResult> startCardCheckout(UUID organisationId, String email, SubscriptionType subscriptionType) {
        return resolveMember(organisationId, email)
                .flatMap(user -> {
                    String txRef = "AI-" + UUID.randomUUID();
                    return createPendingPayment(organisationId, subscriptionType, PaymentProvider.CARD,
                            null, txRef, user.getId())
                            .flatMap(payment -> flutterwaveService.initiateCheckout(
                                            txRef, payment.getExpectedAmount(), payment.getCurrency(), email)
                                    .map(checkoutUrl -> new CardCheckoutResult(payment, checkoutUrl))
                                    .onErrorResume(e -> markFailed(payment, e.getMessage()).then(Mono.error(e))));
                });
    }

    private Mono<Payment> createPendingPayment(UUID organisationId, SubscriptionType subscriptionType,
                                                PaymentProvider provider, String payerPhone,
                                                String providerTransactionId, Long createdBy) {
        return planPriceService.getPrice(subscriptionType)
                .map(planPrice -> {
                    Payment payment = new Payment();
                    payment.setOrganisationId(organisationId);
                    payment.setSubscriptionType(subscriptionType);
                    payment.setProvider(provider);
                    payment.setStatus(PaymentStatus.PENDING);
                    payment.setExpectedAmount(planPrice.getPrice());
                    payment.setCurrency(planPrice.getCurrency());
                    payment.setPayerPhone(payerPhone);
                    payment.setProviderTransactionId(providerTransactionId);
                    payment.setCreatedBy(createdBy);
                    LocalDateTime now = LocalDateTime.now();
                    payment.setCreatedAt(now);
                    payment.setUpdatedAt(now);
                    return payment;
                })
                .flatMap(paymentRepository::save);
    }

    private Mono<Payment> markFailed(Payment payment, String reason) {
        return resolveOutcome(payment, PaymentStatus.FAILED, null, reason);
    }

    /** Provider confirmed the payment, but for less than the fixed price — never activates the subscription. */
    private Mono<Payment> markUnderpaid(Payment payment, BigDecimal receivedAmount) {
        return resolveOutcome(payment, PaymentStatus.UNDERPAID, receivedAmount,
                "Payment could not be completed because the full subscription amount "
                        + "was not received. Please ensure that the required amount is paid.");
    }

    private Mono<Payment> markSuccessfulAndActivate(Payment payment, BigDecimal receivedAmount) {
        return resolveOutcome(payment, PaymentStatus.SUCCESSFUL, receivedAmount, null);
    }

    /** Stale PENDING payment nobody ever resolved — stops the reconciliation sweep from retrying it forever. */
    private Mono<Payment> markExpired(Payment payment) {
        return resolveOutcome(payment, PaymentStatus.EXPIRED, null,
                "Payment timed out waiting for confirmation.");
    }

    /**
     * Atomically claims the PENDING -> {@code newStatus} transition so that only one of several
     * concurrent resolvers for the same payment (a client poll, a provider webhook, a duplicate
     * webhook delivery, or the reconciliation sweep) ever proceeds — see PaymentRepository#resolveIfPending.
     * The loser gets the current (already-resolved) row back untouched instead of re-running
     * {@link SubscriptionActivationService#activateFromPayment}, which closes the race where two
     * winners could each extend/create a subscription for the same payment.
     */
    private Mono<Payment> resolveOutcome(Payment payment, PaymentStatus newStatus,
                                          BigDecimal receivedAmount, String failureReason) {
        return paymentRepository.resolveIfPending(payment.getId(), newStatus, receivedAmount, failureReason)
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return paymentRepository.findById(payment.getId());
                    }
                    payment.setStatus(newStatus);
                    payment.setReceivedAmount(receivedAmount);
                    payment.setFailureReason(failureReason);
                    payment.setUpdatedAt(LocalDateTime.now());
                    return newStatus == PaymentStatus.SUCCESSFUL
                            ? activationService.activateFromPayment(payment).thenReturn(payment)
                            : Mono.just(payment);
                });
    }

    private boolean isUnderpaid(Payment payment, BigDecimal receivedAmount) {
        return receivedAmount != null && receivedAmount.compareTo(payment.getExpectedAmount()) < 0;
    }

    public Mono<Payment> getMomoPaymentStatus(UUID paymentId, String email) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Payment not found")))
                .flatMap(payment -> resolveMember(payment.getOrganisationId(), email).thenReturn(payment))
                .flatMap(payment -> payment.getStatus() != PaymentStatus.PENDING
                        ? Mono.just(payment)
                        : reconcilePendingPayment(payment));
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

        return paymentRepository.findByProviderTransactionId(txRef)
                .switchIfEmpty(Mono.error(new InvalidRecord("Unknown payment reference: " + txRef)))
                .flatMap(payment -> {
                    // Already terminal — duplicate webhook delivery, safely ignored.
                    if (payment.getStatus() != PaymentStatus.PENDING) {
                        return Mono.empty();
                    }
                    return flutterwaveService.verifyTransaction(transactionId)
                            .flatMap(verification -> resolveVerifiedOutcome(payment, verification))
                            .then();
                });
    }

    /**
     * Flutterwave's transaction id in a webhook body is attacker-controlled — a forged webhook
     * could reference someone else's genuinely-successful transaction. Re-verifying with
     * Flutterwave only proves *some* transaction succeeded; it must also match this payment's
     * own tx_ref/currency before we trust it enough to activate a subscription, and the amount
     * must meet the fixed price in full — a lesser (but real) amount is UNDERPAID, not FAILED.
     */
    private Mono<Payment> resolveVerifiedOutcome(Payment payment, FlutterwaveService.VerificationResult verification) {
        if ("pending".equalsIgnoreCase(verification.status())) {
            return Mono.just(payment);
        }
        if (!verification.successful()) {
            return markFailed(payment, "Flutterwave reported status: " + verification.status());
        }
        if (!payment.getProviderTransactionId().equals(verification.txRef())
                || verification.amount() == null
                || !payment.getCurrency().equalsIgnoreCase(verification.currency())) {
            return markFailed(payment, "Verified transaction did not match this payment (tx_ref/currency mismatch)");
        }
        return isUnderpaid(payment, verification.amount())
                ? markUnderpaid(payment, verification.amount())
                : markSuccessfulAndActivate(payment, verification.amount());
    }

    /**
     * pawaPay deposit callback. The callback body is attacker-controlled and only ever used as a
     * trigger — the outcome always comes from re-querying pawaPay's own API (see PawaPayService#getStatus)
     * before anything is marked successful. An unknown deposit id, a non-MoMo payment, or a payment
     * that's already terminal (duplicate delivery) is silently ignored.
     */
    public Mono<Void> handlePawaPayCallback(UUID depositId) {
        return paymentRepository.findById(depositId)
                .filter(payment -> payment.getProvider() == PaymentProvider.MOMO)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .flatMap(this::reconcileMomoPayment)
                .then();
    }

    private Mono<Payment> reconcileMomoPayment(Payment payment) {
        return pawaPayService.getStatus(payment.getId())
                .flatMap(result -> switch (result.status()) {
                    case SUCCESSFUL -> isUnderpaid(payment, result.amount())
                            ? markUnderpaid(payment, result.amount())
                            : markSuccessfulAndActivate(payment, result.amount());
                    case FAILED -> markFailed(payment, result.failureReason());
                    case PENDING -> Mono.just(payment);
                });
    }

    /**
     * Server-side backup for payments abandoned mid-flow (app closed, connection lost): re-checks
     * with the provider instead of waiting on a client poll or webhook that may never arrive.
     * Used by {@code PaymentReconciliationScheduler}.
     */
    public Mono<Payment> reconcilePendingPayment(Payment payment) {
        return switch (payment.getProvider()) {
            case MOMO -> reconcileMomoPayment(payment);
            case CARD -> flutterwaveService.verifyByTxRef(payment.getProviderTransactionId())
                    .flatMap(verification -> resolveVerifiedOutcome(payment, verification));
        };
    }

    /** A PENDING payment too old to still be worth re-checking. */
    public Mono<Payment> expireStalePayment(Payment payment) {
        return markExpired(payment);
    }

    /** The org's current subscription — TRIAL or a paid ACTIVE period. */
    public Mono<Subscription> getActiveSubscription(UUID organisationId, String email) {
        return resolveMember(organisationId, email)
                .then(Mono.defer(() -> subscriptionRepository
                        .findFirstByOrganisationIdOrderByCreatedAtDesc(organisationId)))
                .switchIfEmpty(Mono.error(new InvalidRecord("No subscription found for this organisation")))
                .flatMap(subscription -> subscription.getStatus() == SubscriptionStatus.TRIAL
                                || subscription.getStatus() == SubscriptionStatus.ACTIVE
                        ? Mono.just(subscription)
                        : Mono.error(new InvalidRecord("No active subscription for this organisation")));
    }
}
