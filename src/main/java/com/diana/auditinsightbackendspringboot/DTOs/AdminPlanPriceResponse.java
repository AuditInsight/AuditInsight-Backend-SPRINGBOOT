package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminPlanPriceResponse {
    private SubscriptionType subscriptionType;
    private BigDecimal price;
    private String currency;
    private int durationDays;
    private String updatedByUsername;
    private LocalDateTime updatedAt;
}
