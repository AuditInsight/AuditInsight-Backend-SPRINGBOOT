package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.EvidenceStatus;
import com.diana.auditinsightbackendspringboot.Enum.PaymentMethod;
import com.diana.auditinsightbackendspringboot.Enum.TransactionStatus;
import com.diana.auditinsightbackendspringboot.Enum.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {
    private String id;
    private UUID organisationId;
    private String name;
    private LocalDate date;
    private BigDecimal amount;
    private TransactionType type;
    private PaymentMethod paymentMethod;
    private TransactionStatus status;
    private EvidenceStatus evidenceStatus;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<EvidenceResponse> evidence;  // populated only on GET /:id
}