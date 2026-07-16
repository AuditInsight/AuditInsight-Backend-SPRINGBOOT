package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.Payment;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PaymentRepository extends ReactiveCrudRepository<Payment, UUID> {

    Mono<Payment> findByProviderReference(String providerReference);

    /**
     * Compare-and-set link: only succeeds (returns 1) the first time it's called for a given
     * payment, so duplicate activation attempts (e.g. a retried webhook) can't relink or
     * re-trigger subscription creation for the same payment.
     */
    @Modifying
    @Query("UPDATE payments SET subscription_id = :subscriptionId, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = :paymentId AND subscription_id IS NULL")
    Mono<Integer> linkSubscriptionIfAbsent(UUID paymentId, UUID subscriptionId);
}
