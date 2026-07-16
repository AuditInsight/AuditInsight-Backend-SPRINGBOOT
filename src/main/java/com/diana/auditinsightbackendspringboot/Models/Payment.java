package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.BillingCycle;
import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
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
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("plan_tier")
    private PlanTier planTier;

    @Column("billing_cycle")
    private BillingCycle billingCycle;

    private PaymentProvider provider;

    private PaymentStatus status = PaymentStatus.PENDING;

    @Column("usd_amount")
    private BigDecimal usdAmount;

    @Column("exchange_rate")
    private BigDecimal exchangeRate;

    @Column("charged_currency")
    private String chargedCurrency;

    @Column("charged_amount")
    private BigDecimal chargedAmount;

    @Column("provider_reference")
    private String providerReference;

    @Column("payer_phone")
    private String payerPhone;

    @Column("subscription_id")
    private UUID subscriptionId;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
