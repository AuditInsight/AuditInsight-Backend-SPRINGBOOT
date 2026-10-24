package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Single shared place both payment rails (MoMo and card) call once a payment is confirmed
 * successful. Idempotent: a payment that's already linked to a subscription short-circuits
 * without touching the subscription again, so a duplicate webhook/poll can't create a second
 * subscription or push the end date forward twice.
 */
@Service
public class SubscriptionActivationService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanPriceService planPriceService;

    public SubscriptionActivationService(PaymentRepository paymentRepository,
                                          SubscriptionRepository subscriptionRepository,
                                          PlanPriceService planPriceService) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planPriceService = planPriceService;
    }

    public Mono<Subscription> activateFromPayment(Payment payment) {
        return paymentRepository.findById(payment.getId())
                .switchIfEmpty(Mono.error(new InvalidRecord("Payment not found")))
                .flatMap(freshPayment -> {
                    if (freshPayment.getSubscriptionId() != null) {
                        return subscriptionRepository.findById(freshPayment.getSubscriptionId());
                    }
                    return resolveSubscription(freshPayment)
                            .flatMap(subscription -> linkAndReturn(freshPayment, subscription));
                });
    }

    private Mono<Subscription> linkAndReturn(Payment payment, Subscription subscription) {
        return paymentRepository.linkSubscriptionIfAbsent(payment.getId(), subscription.getId())
                .flatMap(rowsUpdated -> rowsUpdated > 0
                        ? Mono.just(subscription)
                        // Another concurrent activation attempt already linked this payment —
                        // return whatever it was linked to instead of our own (now orphaned) work.
                        : paymentRepository.findById(payment.getId())
                                .flatMap(p -> subscriptionRepository.findById(p.getSubscriptionId())));
    }

    /**
     * A paid activation only ever extends the org's current row when that row is itself an
     * ACTIVE paid subscription (so renewing before expiry keeps remaining time, per the
     * newStartDate=currentExpiry rule). A TRIAL, EXPIRED, or absent row always starts a fresh
     * paid subscription rather than trying to "extend" something that was never a paid period.
     */
    private Mono<Subscription> resolveSubscription(Payment payment) {
        return subscriptionRepository.findByOrganisationIdAndStatus(payment.getOrganisationId(), SubscriptionStatus.ACTIVE)
                .flatMap(existing -> extendExisting(existing, payment))
                .switchIfEmpty(Mono.defer(() -> createNew(payment)));
    }

    private Mono<Subscription> extendExisting(Subscription existing, Payment payment) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = existing.getEndDate() != null && existing.getEndDate().isAfter(now)
                ? existing.getEndDate() : now;

        return addCycle(base, payment).flatMap(endDate -> {
            existing.setSubscriptionType(payment.getSubscriptionType());
            existing.setEndDate(endDate);
            existing.setUpdatedAt(now);
            return subscriptionRepository.save(existing);
        });
    }

    private Mono<Subscription> createNew(Payment payment) {
        LocalDateTime now = LocalDateTime.now();

        return addCycle(now, payment).flatMap(endDate -> {
            Subscription subscription = new Subscription();
            subscription.setOrganisationId(payment.getOrganisationId());
            subscription.setSubscriptionType(payment.getSubscriptionType());
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setStartDate(now);
            subscription.setEndDate(endDate);
            subscription.setCreatedBy(payment.getCreatedBy());
            subscription.setCreatedAt(now);
            subscription.setUpdatedAt(now);
            return subscriptionRepository.save(subscription);
        });
    }

    private Mono<LocalDateTime> addCycle(LocalDateTime base, Payment payment) {
        return planPriceService.getPrice(payment.getSubscriptionType())
                .map(planPrice -> base.plusDays(planPrice.getDurationDays()));
    }

    /**
     * Platform-admin tier change. Updates the org's existing ACTIVE row in place if one exists,
     * or creates the first ACTIVE row if none does — never a second INSERT while one is already
     * ACTIVE, which would violate {@code uq_subscriptions_active_per_org}.
     */
    public Mono<Subscription> adminChangeSubscription(UUID organisationId, SubscriptionType newType,
                                                        LocalDateTime explicitEndDate, Long actingAdminId) {
        return subscriptionRepository.findByOrganisationIdAndStatus(organisationId, SubscriptionStatus.ACTIVE)
                .flatMap(existing -> applyAdminChange(existing, newType, explicitEndDate))
                .switchIfEmpty(Mono.defer(() -> createAdminSubscription(organisationId, newType, explicitEndDate, actingAdminId)));
    }

    private Mono<Subscription> applyAdminChange(Subscription existing, SubscriptionType newType, LocalDateTime explicitEndDate) {
        LocalDateTime now = LocalDateTime.now();
        return resolveEndDate(now, newType, explicitEndDate).flatMap(endDate -> {
            existing.setSubscriptionType(newType);
            existing.setStatus(SubscriptionStatus.ACTIVE);
            if (existing.getStartDate() == null) {
                existing.setStartDate(now);
            }
            existing.setEndDate(endDate);
            existing.setUpdatedAt(now);
            return subscriptionRepository.save(existing);
        });
    }

    private Mono<Subscription> createAdminSubscription(UUID organisationId, SubscriptionType newType,
                                                         LocalDateTime explicitEndDate, Long actingAdminId) {
        LocalDateTime now = LocalDateTime.now();
        return resolveEndDate(now, newType, explicitEndDate).flatMap(endDate -> {
            Subscription subscription = new Subscription();
            subscription.setOrganisationId(organisationId);
            subscription.setSubscriptionType(newType);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setStartDate(now);
            subscription.setEndDate(endDate);
            subscription.setCreatedBy(actingAdminId);
            subscription.setCreatedAt(now);
            subscription.setUpdatedAt(now);
            return subscriptionRepository.save(subscription);
        });
    }

    private Mono<LocalDateTime> resolveEndDate(LocalDateTime now, SubscriptionType newType, LocalDateTime explicitEndDate) {
        if (explicitEndDate != null) {
            return Mono.just(explicitEndDate);
        }
        return planPriceService.getPrice(newType).map(planPrice -> now.plusDays(planPrice.getDurationDays()));
    }
}
