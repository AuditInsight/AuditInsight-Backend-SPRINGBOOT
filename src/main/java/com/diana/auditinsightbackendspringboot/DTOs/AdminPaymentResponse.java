package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminPaymentResponse {
    private UUID id;
    private SubscriptionType subscriptionType;
    private PaymentProvider provider;
    private PaymentStatus status;
    private BigDecimal expectedAmount;
    private BigDecimal receivedAmount;
    private String currency;
    private LocalDateTime createdAt;
}
