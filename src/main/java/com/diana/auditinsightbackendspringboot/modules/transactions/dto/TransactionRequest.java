package com.diana.auditinsightbackendspringboot.modules.transactions.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class TransactionRequest {

    private LocalDate date;

    private Double amount;

    private String counterparty;

    private String type;

    private String source;

    private String status;

    private Integer riskScore;
}