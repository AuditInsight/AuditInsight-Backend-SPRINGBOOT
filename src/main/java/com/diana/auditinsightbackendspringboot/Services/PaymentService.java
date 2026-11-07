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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    /** A MoMo payment younger than this blocks a second one for the same organisation (no double charges). */
    static final long PENDING_WINDOW_MINUTES = 15;

    /** pawaPay still not knowing a deposit this long after we requested it means the request never reached it. */
    static final long NOT_FOUND_GRACE_MINUTES = 5;

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final OrganisationMemberRepository memberRepository;
    private final PawaPayService pawaPayService;
    private final FlutterwaveService flutterwaveService;
    private final SubscriptionActivationService activationService;
    private final PlanPriceService planPriceService;
    private final OrganisationLock organisationLock;

    public PaymentService(PaymentRepository paymentRepository,
                           SubscriptionRepository subscriptionRepository,
                           UserRepository userRepository,
                           OrganisationMemberRepository memberRepository,
                           PawaPayService pawaPayService,
                           FlutterwaveService flutterwaveService,
                           SubscriptionActivationService activationService,
                           PlanPriceService planPriceService,
                           OrganisationLock organisationLock) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.pawaPayService = pawaPayService;
        this.flutterwaveService = flutterwaveService;
        this.activationService = activationService;
        this.planPriceService = planPriceService;
        this.organisationLock = organisationLock;
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

    /**
     * Starts a Mobile Money payment. Only one can be waiting for approval per organisation at a time (checked under
     * the organisation lock), so a double tap or a second member can't trigger two charges.
     * <p>
     * If pawaPay clearly rejects the request, the payment is marked FAILED. If the outcome is unknown (timeout,
     * connection error, server error) the deposit may still have been created, so the payment stays PENDING and is
     * resolved by status checks, the pawaPay callback or the reconciliation sweep — never marked failed blindly.
     */
    public Mono<Payment> startMomoCheckout(UUID organisationId, String email, SubscriptionType subscriptionType,
                                            String phoneNumber) {
        return resolveMember(organisationId, email)
                .flatMap(user -> organisationLock.inTransaction(organisationId, () -> {
                    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PENDING_WINDOW_MINUTES);
                    return paymentRepository.findAllByOrganisationIdAndStatus(organisationId, PaymentStatus.PENDING)
                            .filter(p -> p.getProvider() == PaymentProvider.MOMO
                                    && p.getCreatedAt() != null && p.getCreatedAt().isAfter(cutoff))
                            .hasElements()
                            .flatMap(busy -> busy
                                    ? Mono.<Payment>error(new InvalidRecord("A Mobile Money payment for this organisation "
                                            + "is already waiting for approval. Approve or decline it on your phone, "
                                            + "or try again in a few minutes."))
                                    : createPendingPayment(organisationId, subscriptionType, PaymentProvider.MOMO,
                                            phoneNumber, null, user.getId()));
                }))
                .flatMap(payment -> pawaPayService.requestDeposit(payment.getId(), payment.getExpectedAmount(), phoneNumber)
                        .thenReturn(payment)
                        .onErrorResume(e -> {
                            if (e instanceof PawaPayService.DepositRejectedException) {
                                return markFailed(payment, e.getMessage()).then(Mono.error(new InvalidRecord(
                                        "Mobile Money could not start this payment. Check the phone number and try again.")));
                            }
                            log.warn("pawaPay deposit request for payment {} had an unknown outcome; keeping it pending: {}",
                                    payment.getId(), e.getMessage());
                            return Mono.just(payment);
                        }));
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
        return resolveOutcome(payment, PaymentStatus.PENDING, PaymentStatus.FAILED, null, reason, null);
    }

    /** Stale PENDING payment nobody ever resolved — stops the reconciliation sweep from retrying it forever. */
    private Mono<Payment> markExpired(Payment payment) {
        return resolveOutcome(payment, PaymentStatus.PENDING, PaymentStatus.EXPIRED, null,
                "Payment timed out waiting for confirmation.", null);
    }

    /**
     * Confirmed by the provider: SUCCESSFUL (activates paid time) or, if less than the fixed price arrived, UNDERPAID
     * (never activates). {@code from} is PENDING normally, or EXPIRED when a payment completes after we gave up on it.
     */
    private Mono<Payment> markConfirmed(Payment payment, PaymentStatus from, BigDecimal receivedAmount,
                                        String providerReference) {
        if (isUnderpaid(payment, receivedAmount)) {
            return resolveOutcome(payment, from, PaymentStatus.UNDERPAID, receivedAmount,
                    "Payment could not be completed because the full subscription amount "
                            + "was not received. Please ensure that the required amount is paid.", providerReference);
        }
        return resolveOutcome(payment, from, PaymentStatus.SUCCESSFUL, receivedAmount, null, providerReference);
    }

    /**
     * Atomically claims the {@code from -> newStatus} transition so only one of several concurrent resolvers (a client
     * poll, a provider callback, a duplicate callback, the reconciliation sweep) ever acts on a payment. The status
     * change and, for SUCCESSFUL, the new paid period are written in one transaction under the organisation lock:
     * if activation fails, the status change rolls back and the payment is simply resolved again later.
     */
    private Mono<Payment> resolveOutcome(Payment payment, PaymentStatus from, PaymentStatus newStatus,
                                          BigDecimal receivedAmount, String failureReason, String providerReference) {
        return organisationLock.inTransaction(payment.getOrganisationId(), () -> claim(payment, from, newStatus,
                        receivedAmount, failureReason)
                        .flatMap(won -> {
                            if (!won) {
                                return paymentRepository.findById(payment.getId());
                            }
                            payment.setStatus(newStatus);
                            payment.setReceivedAmount(receivedAmount);
                            payment.setFailureReason(failureReason);
                            payment.setUpdatedAt(LocalDateTime.now());
                            return newStatus == PaymentStatus.SUCCESSFUL
                                    ? activationService.activateFromPayment(payment)
                                            .doOnNext(sub -> payment.setSubscriptionId(sub.getId()))
                                            .thenReturn(payment)
                                    : Mono.just(payment);
                        }))
                .flatMap(resolved -> storeProviderReference(resolved, providerReference));
    }

    private Mono<Boolean> claim(Payment payment, PaymentStatus from, PaymentStatus newStatus,
                                BigDecimal receivedAmount, String failureReason) {
        Mono<Integer> update = from == PaymentStatus.PENDING
                ? paymentRepository.resolveIfPending(payment.getId(), newStatus, receivedAmount, failureReason)
                : paymentRepository.resolveIfStatus(payment.getId(), from, newStatus, receivedAmount, failureReason);
        return update.map(rows -> rows > 0);
    }

    /** Best effort, outside the activation transaction: a problem storing the reference never undoes a payment. */
    private Mono<Payment> storeProviderReference(Payment payment, String providerReference) {
        if (providerReference == null || providerReference.isBlank() || payment.getProviderTransactionId() != null) {
            return Mono.just(payment);
        }
        return paymentRepository.setProviderReferenceIfAbsent(payment.getId(), providerReference)
                .doOnNext(rows -> { if (rows > 0) payment.setProviderTransactionId(providerReference); })
                .thenReturn(payment)
                .onErrorResume(e -> {
                    log.warn("Could not store the provider reference for payment {}: {}", payment.getId(), e.getMessage());
                    return Mono.just(payment);
                });
    }

    private boolean isUnderpaid(Payment payment, BigDecimal receivedAmount) {
        return receivedAmount != null && receivedAmount.compareTo(payment.getExpectedAmount()) < 0;
    }

    /**
     * Status for the app's polling. A PENDING MoMo payment is re-checked with pawaPay; an EXPIRED one is checked once
     * more in case it completed after we stopped waiting.
     */
    public Mono<Payment> getMomoPaymentStatus(UUID paymentId, String email) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Payment not found")))
                .flatMap(payment -> resolveMember(payment.getOrganisationId(), email).thenReturn(payment))
                .flatMap(payment -> {
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        return reconcilePendingPayment(payment);
                    }
                    if (payment.getStatus() == PaymentStatus.EXPIRED && payment.getProvider() == PaymentProvider.MOMO) {
                        return recoverLateMomoPayment(payment);
                    }
                    return Mono.just(payment);
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
        return markConfirmed(payment, PaymentStatus.PENDING, verification.amount(), null);
    }

    /**
     * pawaPay deposit callback. The callback body is attacker-controlled and only ever used as a trigger — the outcome
     * always comes from re-querying pawaPay's own API before anything is marked successful. Handles PENDING payments
     * and EXPIRED ones (a payment approved after we stopped waiting still gets its paid time). Unknown deposit ids,
     * non-MoMo payments and other final statuses (duplicate deliveries) are ignored.
     */
    public Mono<Void> handlePawaPayCallback(UUID depositId) {
        return paymentRepository.findById(depositId)
                .filter(payment -> payment.getProvider() == PaymentProvider.MOMO)
                .flatMap(payment -> switch (payment.getStatus()) {
                    case PENDING -> reconcileMomoPayment(payment);
                    case EXPIRED -> recoverLateMomoPayment(payment);
                    default -> Mono.<Payment>empty();
                })
                .then();
    }

    private Mono<Payment> reconcileMomoPayment(Payment payment) {
        return pawaPayService.getStatus(payment.getId())
                .flatMap(result -> switch (result.status()) {
                    case SUCCESSFUL -> markConfirmed(payment, PaymentStatus.PENDING, result.amount(),
                            result.providerReference());
                    case FAILED -> markFailed(payment, result.failureReason());
                    case PENDING -> Mono.just(payment);
                    case NOT_FOUND -> isPastNotFoundGrace(payment)
                            ? markFailed(payment, "The payment request never reached Mobile Money. No money was taken.")
                            : Mono.just(payment);
                });
    }

    /** An EXPIRED MoMo payment that pawaPay now reports as completed is recovered; anything else is left as is. */
    private Mono<Payment> recoverLateMomoPayment(Payment payment) {
        return pawaPayService.getStatus(payment.getId())
                .flatMap(result -> {
                    if (result.status() != PawaPayService.PawaPayStatus.SUCCESSFUL) {
                        return Mono.just(payment);
                    }
                    log.info("Payment {} completed after it had expired; recovering it", payment.getId());
                    return markConfirmed(payment, PaymentStatus.EXPIRED, result.amount(), result.providerReference());
                });
    }

    private boolean isPastNotFoundGrace(Payment payment) {
        return payment.getCreatedAt() == null
                || payment.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(NOT_FOUND_GRACE_MINUTES));
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

    /**
     * A PENDING payment too old to keep waiting for. MoMo payments get one last check with pawaPay first, so a
     * completed or failed payment is recorded as such; only one that is still unresolved is expired. If pawaPay
     * can't be reached the error propagates and the payment is retried on the next sweep.
     */
    public Mono<Payment> expireStalePayment(Payment payment) {
        if (payment.getProvider() != PaymentProvider.MOMO) {
            return markExpired(payment);
        }
        return reconcileMomoPayment(payment)
                .flatMap(after -> after.getStatus() == PaymentStatus.PENDING ? markExpired(after) : Mono.just(after));
    }

    /**
     * Safety net: SUCCESSFUL payments with no paid period (only possible for payments recorded before activation and
     * status were saved together) get their period now.
     */
    public Flux<Subscription> activateUnlinkedSuccessfulPayments() {
        return paymentRepository.findAllByStatusAndSubscriptionIdIsNull(PaymentStatus.SUCCESSFUL)
                .concatMap(payment -> activationService.activateFromPayment(payment)
                        .doOnNext(sub -> log.warn("Activated paid period {} for previously unlinked payment {}",
                                sub.getId(), payment.getId()))
                        .onErrorResume(e -> {
                            log.error("Could not activate unlinked payment {}: {}", payment.getId(), e.getMessage());
                            return Mono.empty();
                        }));
    }

    /** The paid period running now, or else the next one queued (e.g. bought during a trial). */
    public Mono<Subscription> getActiveSubscription(UUID organisationId, String email) {
        LocalDateTime now = LocalDateTime.now();
        return resolveMember(organisationId, email)
                .then(Mono.defer(() -> activationService.currentPeriod(organisationId, now)
                        .switchIfEmpty(subscriptionRepository
                                .findAllByOrganisationIdAndStatusOrderByEndDateAsc(organisationId, SubscriptionStatus.ACTIVE)
                                .filter(s -> s.getStartDate() != null && s.getStartDate().isAfter(now))
                                .next())))
                .switchIfEmpty(Mono.error(new InvalidRecord("No active subscription for this organisation")));
    }
}
