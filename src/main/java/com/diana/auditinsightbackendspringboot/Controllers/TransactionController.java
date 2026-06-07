package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.CreateTransactionRequest;
import com.diana.auditinsightbackendspringboot.DTOs.TransactionResponse;
import com.diana.auditinsightbackendspringboot.DTOs.UpdateTransactionStatusRequest;
import com.diana.auditinsightbackendspringboot.Services.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Transaction management — create, list, view, update status")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService txnService;

    public TransactionController(TransactionService txnService) {
        this.txnService = txnService;
    }

    @PostMapping
    @Operation(summary = "Create transaction",
               description = "Creates a new transaction. CLIENT and MEMBER only.")
    public Mono<ResponseEntity<TransactionResponse>> create(
            Authentication auth,
            @Valid @RequestBody CreateTransactionRequest req) {
        return txnService.createTransaction(auth.getName(), req)
                .map(r -> new ResponseEntity<>(r, HttpStatus.CREATED));
    }

    @GetMapping
    @Operation(summary = "List transactions",
               description = "Lists all transactions for the given organisation. All members.")
    public Flux<TransactionResponse> list(
            Authentication auth,
            @RequestParam UUID organisationId) {
        return txnService.listTransactions(organisationId, auth.getName());
    }

    @GetMapping("/{txnId}")
    @Operation(summary = "Get transaction",
               description = "Returns a transaction with its linked evidence. All members.")
    public Mono<ResponseEntity<TransactionResponse>> get(
            Authentication auth,
            @PathVariable String txnId) {
        return txnService.getTransaction(txnId, auth.getName())
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{txnId}")
    @Operation(summary = "Update transaction status",
               description = "Updates transaction status. CLIENT and MEMBER only.")
    public Mono<ResponseEntity<TransactionResponse>> updateStatus(
            Authentication auth,
            @PathVariable String txnId,
            @Valid @RequestBody UpdateTransactionStatusRequest req) {
        return txnService.updateStatus(txnId, auth.getName(), req)
                .map(ResponseEntity::ok);
    }
}
