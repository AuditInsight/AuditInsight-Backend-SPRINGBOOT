package com.diana.auditinsightbackendspringboot.modules.transactions.service;

import com.diana.auditinsightbackendspringboot.modules.transactions.dto.TransactionRequest;
import com.diana.auditinsightbackendspringboot.modules.transactions.dto.TransactionResponse;
import com.diana.auditinsightbackendspringboot.modules.transactions.entity.Transaction;
import com.diana.auditinsightbackendspringboot.modules.transactions.mapper.TransactionMapper;
import com.diana.auditinsightbackendspringboot.modules.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    // 🟢 GET ALL TRANSACTIONS
    public List<TransactionResponse> getAll() {
        return repository.findAll()
                .stream()
                .map(t -> TransactionMapper.toResponse(t)) // ✅ safer than method reference
                .collect(Collectors.toList());
    }

    // 🟢 GET BY ID
    public TransactionResponse getById(Long id) {
        Transaction transaction = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        return TransactionMapper.toResponse(transaction);
    }

    // 🟢 CREATE TRANSACTION
    public TransactionResponse create(TransactionRequest request) {

        Transaction transaction = TransactionMapper.toEntity(request);

        Transaction saved = repository.save(transaction);

        return TransactionMapper.toResponse(saved);
    }
}