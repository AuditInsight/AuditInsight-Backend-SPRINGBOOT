package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AdminChangeSubscriptionRequest {

    @NotNull(message = "Subscription type is required (MONTHLY, SIX_MONTHS or ANNUAL)")
    private SubscriptionType subscriptionType;

    /** Optional override; when omitted the end date is computed from the plan's current duration. */
    private LocalDateTime endDate;
}
