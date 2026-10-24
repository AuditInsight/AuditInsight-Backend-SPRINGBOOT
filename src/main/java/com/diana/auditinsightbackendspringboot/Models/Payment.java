package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("payments")
@Getter
@Setter
public class Payment {

    @Id
    @Column("id")
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("subscription_type")
    private SubscriptionType subscriptionType;

    @Column("provider")
    private PaymentProvider provider;

    @Column("status")
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Backend-computed price for the selected {@link #subscriptionType} — never trusted from the client. */
    @Column("expected_amount")
    private BigDecimal expectedAmount;

    /** Amount the provider actually confirms was collected; null until a terminal status is reached. */
    @Column("received_amount")
    private BigDecimal receivedAmount;

    @Column("currency")
    private String currency;

    @Column("provider_transaction_id")
    private String providerTransactionId;

    @Column("payer_phone")
    private String payerPhone;

    @Column("subscription_id")
    private UUID subscriptionId;

    @Column("failure_reason")
    private String failureReason;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
