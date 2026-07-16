package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.BillingCycle;
import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionResponse {
    private UUID id;
    private UUID organisationId;
    private PlanTier planTier;
    private BillingCycle billingCycle;
    private SubscriptionStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
