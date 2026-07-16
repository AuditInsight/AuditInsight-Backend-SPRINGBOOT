package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentStatusResponse {
    private UUID paymentId;
    private PaymentProvider provider;
    private PaymentStatus status;
    private BigDecimal usdAmount;
    private String chargedCurrency;
    private BigDecimal chargedAmount;
    private UUID subscriptionId;
}
