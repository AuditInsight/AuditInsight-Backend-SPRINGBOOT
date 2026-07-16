package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.BillingCycle;
import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartMomoCheckoutRequest {

    @NotNull(message = "Plan tier is required")
    private PlanTier planTier;

    @NotNull(message = "Billing cycle is required (MONTHLY or YEARLY)")
    private BillingCycle billingCycle;

    @NotBlank(message = "Phone number is required for MoMo payments")
    private String phoneNumber;
}
