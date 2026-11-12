package com.ledger.fraud.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A rule's finding: a score from 0 to 1, the reasons in plain language, and the
 * transactions that support them.
 */
public record RuleHit(BigDecimal score, List<String> reasons, List<UUID> evidenceTransactionIds) {
}
