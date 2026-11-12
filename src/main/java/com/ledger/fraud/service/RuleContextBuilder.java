package com.ledger.fraud.service;

import com.ledger.entity.Account;
import com.ledger.entity.LedgerEvent;
import com.ledger.entity.Transaction;
import com.ledger.enums.EventType;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.fraud.rules.AccountActivity;
import com.ledger.fraud.rules.RuleContext;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.LedgerEventRepository;
import com.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds what a rule sees for one trigger event from the immutable ledger, using only events
 * recorded at or before the trigger. Screening and replay both go through here, which is what
 * makes a replay reproduce the original decision.
 */
@Component
@RequiredArgsConstructor
public class RuleContextBuilder {

    private final LedgerEventRepository ledgerEventRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public RuleContext build(UUID triggerEventId, Duration lookback) {
        LedgerEvent trigger = ledgerEventRepository.findById(triggerEventId)
                .orElseThrow(() -> new IllegalStateException("Ledger event not found: " + triggerEventId));
        Account account = accountRepository.findById(trigger.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + trigger.getAccountId()));

        List<LedgerEvent> events = ledgerEventRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                trigger.getAccountId(), trigger.getCreatedAt().minus(lookback), trigger.getCreatedAt());
        Map<UUID, Transaction> transactions = transactionRepository
                .findAllById(events.stream().map(LedgerEvent::getTransactionId).distinct().toList())
                .stream().collect(Collectors.toMap(Transaction::getId, Function.identity()));

        List<AccountActivity> history = events.stream()
                .map(event -> toActivity(event, transactions.get(event.getTransactionId())))
                .toList();
        AccountActivity triggerActivity = history.stream()
                .filter(activity -> activity.eventId().equals(triggerEventId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Trigger event missing from its own window: " + triggerEventId));

        return new RuleContext(account.getId(), account.getCreatedAt(), triggerActivity, history);
    }

    private static AccountActivity toActivity(LedgerEvent event, Transaction transaction) {
        UUID counterparty = null;
        if (transaction != null) {
            counterparty = event.getEventType() == EventType.CREDIT
                    ? transaction.getFromAccountId()
                    : transaction.getToAccountId();
        }
        return new AccountActivity(event.getId(), event.getTransactionId(), event.getEventType(),
                event.getAmount(), event.getCreatedAt(), counterparty);
    }
}
