package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Server-side backup for payments the client never got to resolve — a MoMo prompt approved after
 * the app was closed, a webhook that never arrived, a browser tab closed mid-card-checkout.
 * Mirrors {@link SubscriptionExpiryScheduler}'s fire-and-subscribe pattern for {@code @Scheduled}.
 */
@Component
@Slf4j
public class PaymentReconciliationScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Value("${payment.reconciliation.stale-after-minutes:15}")
    private long staleAfterMinutes;

    @Value("${payment.reconciliation.expire-after-hours:24}")
    private long expireAfterHours;

    public PaymentReconciliationScheduler(PaymentRepository paymentRepository, PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedRateString = "${payment.reconciliation.interval-ms:600000}")
    public void reconcilePendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleCutoff = now.minusMinutes(staleAfterMinutes);
        LocalDateTime expireCutoff = now.minusHours(expireAfterHours);

        paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, staleCutoff)
                .flatMap(payment -> resolve(payment, expireCutoff))
                .doOnError(e -> log.error("Payment reconciliation sweep failed: {}", e.getMessage()))
                .subscribe();
    }

    private Mono<Payment> resolve(Payment payment, LocalDateTime expireCutoff) {
        Mono<Payment> outcome = payment.getCreatedAt().isBefore(expireCutoff)
                ? paymentService.expireStalePayment(payment)
                : paymentService.reconcilePendingPayment(payment);

        return outcome.onErrorResume(e -> {
            log.error("Failed to reconcile pending payment {}: {}", payment.getId(), e.getMessage());
            return Mono.empty();
        });
    }
}
