package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface PaymentRepository extends ReactiveCrudRepository<Payment, UUID> {

    Mono<Payment> findByProviderTransactionId(String providerTransactionId);

    Flux<Payment> findAllByOrganisationIdOrderByCreatedAtDesc(UUID organisationId);

    Flux<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime cutoff);

    /**
     * Compare-and-set link: only succeeds (returns 1) the first time it's called for a given
     * payment, so duplicate activation attempts (e.g. a retried webhook) can't relink or
     * re-trigger subscription creation for the same payment.
     */
    @Modifying
    @Query("UPDATE payments SET subscription_id = :subscriptionId, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = :paymentId AND subscription_id IS NULL")
    Mono<Integer> linkSubscriptionIfAbsent(UUID paymentId, UUID subscriptionId);

    /**
     * Compare-and-set outcome resolution: only the caller that gets back 1 wins the right to act
     * on this payment (mark it terminal and, for SUCCESSFUL, activate the subscription). A second
     * concurrent resolver for the same payment — a client poll racing the pawaPay/Flutterwave
     * webhook, or a duplicate webhook delivery — gets back 0 and must treat it as a no-op.
     */
    @Modifying
    @Query("UPDATE payments SET status = :status, received_amount = :receivedAmount, " +
            "failure_reason = :failureReason, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = :id AND status = 'PENDING'")
    Mono<Integer> resolveIfPending(UUID id, PaymentStatus status, BigDecimal receivedAmount, String failureReason);
}
