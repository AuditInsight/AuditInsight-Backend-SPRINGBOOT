package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.BillingCycle;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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

    public SubscriptionActivationService(PaymentRepository paymentRepository,
                                          SubscriptionRepository subscriptionRepository) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
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

    private Mono<Subscription> resolveSubscription(Payment payment) {
        return subscriptionRepository.findByOrganisationIdAndStatus(payment.getOrganisationId(), SubscriptionStatus.ACTIVE)
                .flatMap(existing -> extendExisting(existing, payment))
                .switchIfEmpty(Mono.defer(() -> createNew(payment)));
    }

    private Mono<Subscription> extendExisting(Subscription existing, Payment payment) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = existing.getEndDate() != null && existing.getEndDate().isAfter(now)
                ? existing.getEndDate() : now;

        existing.setPlanTier(payment.getPlanTier());
        existing.setBillingCycle(payment.getBillingCycle());
        existing.setEndDate(addCycle(base, payment.getBillingCycle()));
        existing.setUpdatedAt(now);
        return subscriptionRepository.save(existing);
    }

    private Mono<Subscription> createNew(Payment payment) {
        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = new Subscription();
        subscription.setOrganisationId(payment.getOrganisationId());
        subscription.setPlanTier(payment.getPlanTier());
        subscription.setBillingCycle(payment.getBillingCycle());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(now);
        subscription.setEndDate(addCycle(now, payment.getBillingCycle()));
        subscription.setCreatedBy(payment.getCreatedBy());
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        return subscriptionRepository.save(subscription);
    }

    private LocalDateTime addCycle(LocalDateTime base, BillingCycle cycle) {
        return cycle == BillingCycle.YEARLY ? base.plusYears(1) : base.plusMonths(1);
    }
}
