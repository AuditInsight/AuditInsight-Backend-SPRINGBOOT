package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTransactionStatusRequest {

    @NotNull(message = "Status is required (PENDING or COMPLETED)")
    private TransactionStatus status;
}