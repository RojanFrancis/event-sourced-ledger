package com.ledger.fraud.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The flag's rule run again on the ledger as it stood at the flag's {@code asOf}.
 * {@code matches} is true when the replay reproduces the stored score, reasons and evidence exactly.
 */
public record ReplayResponse(
        UUID flagId,
        String ruleId,
        int ruleVersion,
        LocalDateTime asOf,
        boolean matches,
        BigDecimal originalScore,
        BigDecimal replayedScore,
        List<String> originalReasons,
        List<String> replayedReasons,
        List<UUID> originalEvidence,
        List<UUID> replayedEvidence,
        int eventsConsidered,
        BigDecimal balanceAsOf) {
}
