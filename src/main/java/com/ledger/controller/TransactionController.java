package com.ledger.controller;

import com.ledger.dto.request.DepositRequest;
import com.ledger.dto.request.TransferRequest;
import com.ledger.dto.request.WithdrawRequest;
import com.ledger.dto.response.TransactionResponse;
import com.ledger.entity.Transaction;
import com.ledger.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // Transfer between two accounts
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request) {
        Transaction tx = transactionService.transfer(
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount(),
                request.getDescription(),
                request.getIdempotencyKey()
        );
        return ResponseEntity.ok(toResponse(tx));
    }

    // Deposit into account
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @Valid @RequestBody DepositRequest request) {
        Transaction tx = transactionService.deposit(
                request.getAccountId(),
                request.getAmount(),
                request.getDescription(),
                request.getIdempotencyKey()
        );
        return ResponseEntity.ok(toResponse(tx));
    }

    // Withdraw from account
    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @Valid @RequestBody WithdrawRequest request) {
        Transaction tx = transactionService.withdraw(
                request.getAccountId(),
                request.getAmount(),
                request.getDescription(),
                request.getIdempotencyKey()
        );
        return ResponseEntity.ok(toResponse(tx));
    }

    // Get transaction by ID
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {
        Transaction tx = transactionService.getTransaction(id);
        return ResponseEntity.ok(toResponse(tx));
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .fromAccountId(tx.getFromAccountId())
                .toAccountId(tx.getToAccountId())
                .amount(tx.getAmount())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .initiatedAt(tx.getInitiatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
