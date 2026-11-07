package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionSource;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Turns a confirmed payment into paid time, and applies Super Admin changes to paid time.
 * <p>
 * Every successful payment buys exactly one new paid period ({@code source = PAYMENT}) of its plan's length. The
 * period starts at the latest of: now, the end of the organisation's current trial, and the end of the paid time it
 * already has — so paying during a trial starts paid time when the trial ends, and renewing early never loses days.
 * <p>
 * Runs under {@link OrganisationLock}, so two payments for the same organisation can't compute the same start date
 * and overlap. Idempotent: a payment already linked to a period returns that period instead of buying another.
 */
@Service
public class SubscriptionActivationService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TrialGrantRepository trialGrantRepository;
    private final PlanPriceService planPriceService;
    private final OrganisationLock organisationLock;

    public SubscriptionActivationService(PaymentRepository paymentRepository,
                                          SubscriptionRepository subscriptionRepository,
                                          TrialGrantRepository trialGrantRepository,
                                          PlanPriceService planPriceService,
                                          OrganisationLock organisationLock) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.trialGrantRepository = trialGrantRepository;
        this.planPriceService = planPriceService;
        this.organisationLock = organisationLock;
    }

    public Mono<Subscription> activateFromPayment(Payment payment) {
        return organisationLock.inTransaction(payment.getOrganisationId(), () -> paymentRepository.findById(payment.getId())
                .switchIfEmpty(Mono.error(new InvalidRecord("Payment not found")))
                .flatMap(fresh -> fresh.getSubscriptionId() != null
                        ? subscriptionRepository.findById(fresh.getSubscriptionId())
                        : createPaidPeriod(fresh).flatMap(period -> linkAndReturn(fresh, period))));
    }

    /** When newly bought paid time would start for this organisation: max(now, current trial end, paid time end). */
    public Mono<LocalDateTime> nextPaidStart(UUID organisationId) {
        LocalDateTime now = LocalDateTime.now();
        Mono<LocalDateTime> paidEnd = subscriptionRepository
                .findAllByOrganisationIdAndStatusOrderByEndDateAsc(organisationId, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndDate() != null && s.getEndDate().isAfter(now))
                .map(Subscription::getEndDate)
                .reduce(now, SubscriptionActivationService::later);
        Mono<LocalDateTime> trialEnd = trialGrantRepository
                .findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE)
                .filter(g -> g.isCurrent(now))
                .map(g -> g.getEndsAt())
                .defaultIfEmpty(now);
        return Mono.zip(paidEnd, trialEnd).map(t -> later(t.getT1(), t.getT2()));
    }

    private Mono<Subscription> createPaidPeriod(Payment payment) {
        return Mono.zip(nextPaidStart(payment.getOrganisationId()), planPriceService.getPrice(payment.getSubscriptionType()))
                .flatMap(t -> {
                    LocalDateTime start = t.getT1();
                    LocalDateTime now = LocalDateTime.now();
                    Subscription period = new Subscription();
                    period.setOrganisationId(payment.getOrganisationId());
                    period.setSubscriptionType(payment.getSubscriptionType());
                    period.setStatus(SubscriptionStatus.ACTIVE);
                    period.setSource(SubscriptionSource.PAYMENT);
                    period.setStartDate(start);
                    period.setEndDate(start.plusDays(t.getT2().getDurationDays()));
                    period.setCreatedBy(payment.getCreatedBy());
                    period.setCreatedAt(now);
                    period.setUpdatedAt(now);
                    return subscriptionRepository.save(period);
                });
    }

    private Mono<Subscription> linkAndReturn(Payment payment, Subscription subscription) {
        return paymentRepository.linkSubscriptionIfAbsent(payment.getId(), subscription.getId())
                .flatMap(rowsUpdated -> rowsUpdated > 0
                        ? Mono.just(subscription)
                        // Defensive: the lock makes this unreachable, but never buy two periods for one payment.
                        : Mono.error(new IllegalStateException("Payment " + payment.getId() + " was already linked")));
    }

    // ── Super Admin changes ───────────────────────────────────────────────────────

    /**
     * Super Admin tier change. If a paid period is running now, its plan and end date are changed in place (paid
     * periods queued after it move by the same amount, keeping their length). Otherwise a complimentary period
     * ({@code source = ADMIN_COMPLIMENTARY}) starts now. {@code explicitEndDate} overrides the plan length.
     */
    public Mono<Subscription> adminChangeSubscription(UUID organisationId, SubscriptionType newType,
                                                        LocalDateTime explicitEndDate, Long actingAdminId,
                                                        String reason) {
        LocalDateTime now = LocalDateTime.now();
        if (explicitEndDate != null && !explicitEndDate.isAfter(now)) {
            return Mono.error(new InvalidRecord("The end date must be in the future"));
        }
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        return organisationLock.inTransaction(organisationId, () -> currentPeriod(organisationId, now)
                .flatMap(current -> changeCurrent(current, newType, explicitEndDate, cleanReason, now))
                .switchIfEmpty(Mono.defer(() -> createComplimentary(organisationId, newType, explicitEndDate,
                        actingAdminId, cleanReason, now))));
    }

    /** The paid period running now, if any. */
    public Mono<Subscription> currentPeriod(UUID organisationId, LocalDateTime now) {
        return subscriptionRepository.findAllByOrganisationIdAndStatusOrderByEndDateAsc(organisationId, SubscriptionStatus.ACTIVE)
                .filter(s -> EntitlementService.isCurrent(s, now))
                .next();
    }

    private Mono<Subscription> changeCurrent(Subscription current, SubscriptionType newType, LocalDateTime explicitEndDate,
                                             String reason, LocalDateTime now) {
        LocalDateTime start = current.getStartDate() == null ? now : current.getStartDate();
        return resolveEndDate(start, newType, explicitEndDate).flatMap(newEnd -> {
            LocalDateTime oldEnd = current.getEndDate();
            current.setSubscriptionType(newType);
            current.setStartDate(start);
            current.setEndDate(newEnd);
            current.setAdminReason(reason);
            current.setExpiryReminderSent(false);
            current.setUpdatedAt(now);
            return subscriptionRepository.save(current)
                    .flatMap(saved -> shiftQueuedPeriods(saved, oldEnd, Duration.between(oldEnd, newEnd), now)
                            .thenReturn(saved));
        });
    }

    private Mono<Void> shiftQueuedPeriods(Subscription changed, LocalDateTime oldEnd, Duration delta, LocalDateTime now) {
        if (delta.isZero()) return Mono.empty();
        return subscriptionRepository
                .findAllByOrganisationIdAndStatusOrderByEndDateAsc(changed.getOrganisationId(), SubscriptionStatus.ACTIVE)
                .filter(s -> !s.getId().equals(changed.getId()) && s.getStartDate() != null
                        && s.getStartDate().isAfter(now) && !s.getStartDate().isBefore(oldEnd))
                .concatMap(s -> {
                    s.setStartDate(s.getStartDate().plus(delta));
                    s.setEndDate(s.getEndDate().plus(delta));
                    s.setUpdatedAt(now);
                    return subscriptionRepository.save(s);
                })
                .then();
    }

    private Mono<Subscription> createComplimentary(UUID organisationId, SubscriptionType newType,
                                                   LocalDateTime explicitEndDate, Long actingAdminId, String reason,
                                                   LocalDateTime now) {
        return resolveEndDate(now, newType, explicitEndDate).flatMap(endDate -> {
            Subscription period = new Subscription();
            period.setOrganisationId(organisationId);
            period.setSubscriptionType(newType);
            period.setStatus(SubscriptionStatus.ACTIVE);
            period.setSource(SubscriptionSource.ADMIN_COMPLIMENTARY);
            period.setAdminReason(reason);
            period.setStartDate(now);
            period.setEndDate(endDate);
            period.setCreatedBy(actingAdminId);
            period.setCreatedAt(now);
            period.setUpdatedAt(now);
            return subscriptionRepository.save(period);
        });
    }

    private Mono<LocalDateTime> resolveEndDate(LocalDateTime start, SubscriptionType newType, LocalDateTime explicitEndDate) {
        if (explicitEndDate != null) {
            return Mono.just(explicitEndDate);
        }
        return planPriceService.getPrice(newType).map(planPrice -> start.plusDays(planPrice.getDurationDays()));
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
