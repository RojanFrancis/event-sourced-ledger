package com.ledger.fraud.rules;

import com.ledger.enums.EventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One ledger event as the fraud rules see it: the event plus the other side of its transaction.
 * The counterparty is null for deposits and withdrawals, which have no other account.
 */
public record AccountActivity(
        UUID eventId,
        UUID transactionId,
        EventType type,
        BigDecimal amount,
        LocalDateTime at,
        UUID counterpartyAccountId) {
}
