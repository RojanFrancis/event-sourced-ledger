package com.ledger.fraud.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One transaction behind a flag, seen from the flagged account: money in (CREDIT) or out (DEBIT),
 * and the other side if there is one. Names are masked, as an analyst only needs to tell parties apart.
 */
public record EvidenceResponse(
        UUID transactionId,
        LocalDateTime at,
        String direction,
        BigDecimal amount,
        UUID counterpartyAccountId,
        String counterparty,
        BigDecimal balanceAfter) {
}
