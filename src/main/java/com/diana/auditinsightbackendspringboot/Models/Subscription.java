package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.BillingCycle;
import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("subscriptions")
@Getter
@Setter
public class Subscription {

    @Id
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("plan_tier")
    private PlanTier planTier;

    @Column("billing_cycle")
    private BillingCycle billingCycle;

    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Column("start_date")
    private LocalDateTime startDate;

    @Column("end_date")
    private LocalDateTime endDate;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
