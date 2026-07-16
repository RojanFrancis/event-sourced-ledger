package com.ledger.service;

import com.ledger.entity.LedgerEvent;
import com.ledger.enums.EventType;
import com.ledger.exception.InsufficientFundsException;
import com.ledger.repository.LedgerEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEventRepository ledgerEventRepository;

    /**
     * THE CORE METHOD — derives current balance by replaying all events.
     *
     * This is the heart of event sourcing:
     * We never store balance directly. We compute it fresh from history.
     *
     * Interview question you WILL get: "Why not just store the balance?"
     * Answer: Because events are immutable truth. If a bug corrupts a balance,
     * you can always recompute from events. You can never go back and fix
     * corrupted mutable state.
     */
    public BigDecimal computeCurrentBalance(UUID accountId) {
        List<LedgerEvent> events = ledgerEventRepository
                .findByAccountIdOrderByCreatedAtAsc(accountId);

        return computeBalanceFromEvents(events);
    }

    /**
     * Point-in-time balance — what was the balance at a specific moment?
     * This is IMPOSSIBLE with a normal CRUD balance column.
     * With event sourcing, it's trivial.
     */
    public BigDecimal computeBalanceAsOf(UUID accountId, LocalDateTime asOf) {
        List<LedgerEvent> events = ledgerEventRepository
                .findByAccountIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc(accountId, asOf);

        log.debug("Computing balance for account {} as of {} from {} events",
                accountId, asOf, events.size());

        return computeBalanceFromEvents(events);
    }

    /**
     * The actual balance computation logic.
     * CREDIT events add to balance, DEBIT events subtract.
     */
    private BigDecimal computeBalanceFromEvents(List<LedgerEvent> events) {
        return events.stream()
                .map(event -> {
                    if (event.getEventType() == EventType.CREDIT) {
                        return event.getAmount();
                    } else {
                        return event.getAmount().negate();
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Records a CREDIT event (money coming into account).
     * Validates and appends an immutable event to the ledger.
     */
    public LedgerEvent recordCredit(UUID accountId, UUID transactionId,
                                    BigDecimal amount, String description,
                                    String idempotencyKey) {

        // Check idempotency — don't process same event twice
        if (ledgerEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("Duplicate credit attempt blocked. Idempotency key: {}", idempotencyKey);
            throw new RuntimeException("Duplicate transaction detected: " + idempotencyKey);
        }

        BigDecimal currentBalance = computeCurrentBalance(accountId);
        BigDecimal newBalance = currentBalance.add(amount);

        LedgerEvent event = LedgerEvent.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .eventType(EventType.CREDIT)
                .amount(amount)
                .balanceAfter(newBalance)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .build();

        LedgerEvent saved = ledgerEventRepository.save(event);
        log.info("CREDIT recorded: accountId={}, amount={}, newBalance={}", accountId, amount, newBalance);
        return saved;
    }

    /**
     * Records a DEBIT event (money leaving account).
     * Validates sufficient funds BEFORE recording.
     */
    public LedgerEvent recordDebit(UUID accountId, UUID transactionId,
                                   BigDecimal amount, String description,
                                   String idempotencyKey) {

        // Check idempotency
        if (ledgerEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("Duplicate debit attempt blocked. Idempotency key: {}", idempotencyKey);
            throw new RuntimeException("Duplicate transaction detected: " + idempotencyKey);
        }

        BigDecimal currentBalance = computeCurrentBalance(accountId);

        // Enforce overdraft protection
        if (currentBalance.compareTo(amount) < 0) {
            log.warn("Insufficient funds: accountId={}, balance={}, requested={}",
                    accountId, currentBalance, amount);
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + currentBalance + ", Requested: " + amount);
        }

        BigDecimal newBalance = currentBalance.subtract(amount);

        LedgerEvent event = LedgerEvent.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .eventType(EventType.DEBIT)
                .amount(amount)
                .balanceAfter(newBalance)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .build();

        LedgerEvent saved = ledgerEventRepository.save(event);
        log.info("DEBIT recorded: accountId={}, amount={}, newBalance={}", accountId, amount, newBalance);
        return saved;
    }

    /**
     * Get full event history for an account — the complete audit trail.
     */
    public List<LedgerEvent> getEventHistory(UUID accountId) {
        return ledgerEventRepository.findByAccountIdOrderByCreatedAtAsc(accountId);
    }

    /**
     * Get events within a date range — for statement generation.
     */
    public List<LedgerEvent> getEventsInRange(UUID accountId,
                                               LocalDateTime from,
                                               LocalDateTime to) {
        return ledgerEventRepository
                .findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(accountId, from, to);
    }
}
