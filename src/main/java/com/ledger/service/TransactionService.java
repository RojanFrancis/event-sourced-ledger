package com.ledger.service;

import com.ledger.entity.Account;
import com.ledger.entity.Transaction;
import com.ledger.enums.TransactionStatus;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    /**
     * TRANSFER — moves money between two accounts atomically.
     *
     * This is the most important method in the system.
     *
     * Key concept: Double-entry bookkeeping
     * - One DEBIT event on the sender's account
     * - One CREDIT event on the receiver's account
     * - Both must succeed or neither applies (@Transactional ensures this)
     *
     * @Transactional means: if the credit fails after the debit succeeds,
     * the whole thing rolls back. Money never disappears.
     */
    @Transactional
    public Transaction transfer(UUID fromAccountId, UUID toAccountId,
                                BigDecimal amount, String description,
                                String idempotencyKey) {

        log.info("Transfer initiated: from={}, to={}, amount={}", fromAccountId, toAccountId, amount);

        // Validate both accounts exist and lock them. Locking in a fixed order
        // (by id) means two opposite transfers can't deadlock each other.
        Account fromAccount;
        Account toAccount;
        if (fromAccountId.compareTo(toAccountId) <= 0) {
            fromAccount = lockAccount(fromAccountId, "Sender account not found: ");
            toAccount = lockAccount(toAccountId, "Receiver account not found: ");
        } else {
            toAccount = lockAccount(toAccountId, "Receiver account not found: ");
            fromAccount = lockAccount(fromAccountId, "Sender account not found: ");
        }

        // Prevent duplicate transfers on retry. This runs after the locks on purpose:
        // under MySQL REPEATABLE READ the first plain SELECT fixes the transaction's
        // snapshot, and it must not be taken before we hold the locks.
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("Duplicate transfer blocked: {}", idempotencyKey);
            return transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        // Create the transaction record first (status = PENDING)
        Transaction transaction = Transaction.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.PENDING)
                .build();
        transaction = transactionRepository.save(transaction);

        try {
            // Step 1: Debit the sender
            // This will throw InsufficientFundsException if balance is too low
            ledgerService.recordDebit(
                    fromAccountId,
                    transaction.getId(),
                    amount,
                    "Transfer to " + toAccount.getOwnerName() + ": " + description,
                    idempotencyKey + ":debit"
            );

            // Step 2: Credit the receiver
            ledgerService.recordCredit(
                    toAccountId,
                    transaction.getId(),
                    amount,
                    "Transfer from " + fromAccount.getOwnerName() + ": " + description,
                    idempotencyKey + ":credit"
            );

            // Mark transaction as completed
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            transaction = transactionRepository.save(transaction);

            log.info("Transfer completed: transactionId={}", transaction.getId());

        } catch (Exception e) {
            // If anything fails, mark transaction as failed
            // @Transactional will rollback the ledger events too
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            log.error("Transfer failed: {}", e.getMessage());
            throw e;
        }

        return transaction;
    }

    /**
     * DEPOSIT — adds money to an account (money coming from outside the system).
     */
    @Transactional
    public Transaction deposit(UUID toAccountId, BigDecimal amount,
                               String description, String idempotencyKey) {

        lockAccount(toAccountId, "Account not found: ");

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        Transaction transaction = Transaction.builder()
                .toAccountId(toAccountId)
                .amount(amount)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.PENDING)
                .build();
        transaction = transactionRepository.save(transaction);

        ledgerService.recordCredit(toAccountId, transaction.getId(),
                amount, description, idempotencyKey + ":credit");

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        return transactionRepository.save(transaction);
    }

    /**
     * WITHDRAW — removes money from an account.
     */
    @Transactional
    public Transaction withdraw(UUID fromAccountId, BigDecimal amount,
                                String description, String idempotencyKey) {

        lockAccount(fromAccountId, "Account not found: ");

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        Transaction transaction = Transaction.builder()
                .fromAccountId(fromAccountId)
                .amount(amount)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.PENDING)
                .build();
        transaction = transactionRepository.save(transaction);

        ledgerService.recordDebit(fromAccountId, transaction.getId(),
                amount, description, idempotencyKey + ":debit");

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        return transactionRepository.save(transaction);
    }

    private Account lockAccount(UUID accountId, String notFoundMessage) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(notFoundMessage + accountId));
    }

    public Transaction getTransaction(UUID id) {
    return transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));
    }
}
