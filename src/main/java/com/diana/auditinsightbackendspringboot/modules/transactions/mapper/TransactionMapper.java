package com.diana.auditinsightbackendspringboot.modules.transactions.mapper;

import com.diana.auditinsightbackendspringboot.modules.transactions.dto.TransactionRequest;
import com.diana.auditinsightbackendspringboot.modules.transactions.dto.TransactionResponse;
import com.diana.auditinsightbackendspringboot.modules.transactions.entity.Transaction;

public class TransactionMapper {

    // DTO → ENTITY (🔥 CLEAN BUILDER VERSION)
    public static Transaction toEntity(TransactionRequest dto) {
        return Transaction.builder()
                .date(dto.getDate())
                .amount(dto.getAmount())
                .counterparty(dto.getCounterparty())
                .type(dto.getType())
                .source(dto.getSource())
                .status(dto.getStatus())
                .riskScore(dto.getRiskScore())
                .build();
    }

    // ENTITY → DTO
    public static TransactionResponse toResponse(Transaction t) {
        TransactionResponse dto = new TransactionResponse();
        dto.setId(t.getId());
        dto.setDate(t.getDate());
        dto.setAmount(t.getAmount());
        dto.setCounterparty(t.getCounterparty());
        dto.setType(t.getType());
        dto.setSource(t.getSource());
        dto.setStatus(t.getStatus());
        dto.setRiskScore(t.getRiskScore());
        return dto;
    }
}