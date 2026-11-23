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
            "failure_reason = :failureReason, updated_at = CURRENT_TIMESTAMP, resolved_at = CURRENT_TIMESTAMP " +
            "WHERE id = :id AND status = 'PENDING'")
    Mono<Integer> resolveIfPending(UUID id, PaymentStatus status, BigDecimal receivedAmount, String failureReason);

    /** Same compare-and-set as {@link #resolveIfPending}, from any expected status (used to recover a late success). */
    @Modifying
    @Query("UPDATE payments SET status = :status, received_amount = :receivedAmount, " +
            "failure_reason = :failureReason, updated_at = CURRENT_TIMESTAMP, resolved_at = CURRENT_TIMESTAMP " +
            "WHERE id = :id AND status = :expected")
    Mono<Integer> resolveIfStatus(UUID id, PaymentStatus expected, PaymentStatus status, BigDecimal receivedAmount,
                                  String failureReason);

    /** Stores the operator's transaction id once, for support and reconciliation. */
    @Modifying
    @Query("UPDATE payments SET provider_transaction_id = :reference, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = :id AND provider_transaction_id IS NULL")
    Mono<Integer> setProviderReferenceIfAbsent(UUID id, String reference);

    Flux<Payment> findAllByOrganisationIdAndStatus(UUID organisationId, PaymentStatus status);

    // ── Super Admin payments ledger ────────────────────────────────────────────────────────────
    // Every filter is optional (null = any). {@code search} matches, case-insensitively, the organisation name, the
    // payer's name or email, the phone charged, or the provider reference. {@code fromTime} is inclusive and
    // {@code toTime} exclusive, both on when the payment was started.

    String LEDGER_FROM_WHERE = "FROM payments p " +
            "LEFT JOIN organisation o ON o.id = p.organisation_id " +
            "LEFT JOIN users u ON u.id = p.created_by " +
            "WHERE (:status IS NULL OR p.status = :status) " +
            "AND (:provider IS NULL OR p.provider = :provider) " +
            "AND (:organisationId IS NULL OR p.organisation_id = :organisationId) " +
            "AND (:fromTime IS NULL OR p.created_at >= :fromTime) " +
            "AND (:toTime IS NULL OR p.created_at < :toTime) " +
            "AND (:search IS NULL " +
            "  OR LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "  OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "  OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "  OR p.payer_phone LIKE CONCAT('%', :search, '%') " +
            "  OR LOWER(p.provider_transaction_id) LIKE LOWER(CONCAT('%', :search, '%'))) ";

    @Query("SELECT p.* " + LEDGER_FROM_WHERE + "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Payment> findLedgerPage(String status, String provider, UUID organisationId, LocalDateTime fromTime,
                                 LocalDateTime toTime, String search, int limit, long offset);

    /** Count and money received per status and currency, for the same filters as the ledger. */
    @Query("SELECT p.status AS status, p.currency AS currency, COUNT(*) AS payments, " +
            "COALESCE(SUM(p.received_amount), 0) AS received " + LEDGER_FROM_WHERE +
            "GROUP BY p.status, p.currency ORDER BY p.status, p.currency")
    Flux<com.diana.auditinsightbackendspringboot.DTOs.PaymentLedgerTotal> ledgerTotals(
            String status, String provider, UUID organisationId, LocalDateTime fromTime, LocalDateTime toTime,
            String search);

    @Query("SELECT COUNT(*) " + LEDGER_FROM_WHERE)
    Mono<Long> countLedger(String status, String provider, UUID organisationId, LocalDateTime fromTime,
                           LocalDateTime toTime, String search);

    /** Successful payments that never got their paid period (activation failed after the status was saved). */
    Flux<Payment> findAllByStatusAndSubscriptionIdIsNull(PaymentStatus status);

    /** True when a successful payment bought this period (anything else is free access). */
    Mono<Boolean> existsBySubscriptionIdAndStatus(UUID subscriptionId, PaymentStatus status);
}
