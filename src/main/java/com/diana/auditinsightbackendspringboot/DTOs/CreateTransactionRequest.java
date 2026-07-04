package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.PaymentMethod;
import com.diana.auditinsightbackendspringboot.Enum.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class CreateTransactionRequest {

    @NotNull(message = "Organisation ID is required")
    private UUID organisationId;

    @NotBlank(message = "Transaction name is required")
    private String name;

    @NotNull(message = "Transaction date is required")
    private LocalDate date;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Transaction type is required (INCOME or EXPENSE)")
    private TransactionType type;

    @NotNull(message = "Payment method is required (BANK, MOBILE_MONEY, or CASH)")
    private PaymentMethod paymentMethod;

    @NotBlank(message = "the counterparty is needed")
    private String counterparty;
}