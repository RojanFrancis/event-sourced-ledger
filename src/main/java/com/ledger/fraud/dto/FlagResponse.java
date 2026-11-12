package com.ledger.fraud.dto;

import com.ledger.fraud.entity.AnalystDecision;
import com.ledger.fraud.entity.FraudFlag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** A flag with its review history; status is the latest decision, or OPEN if none. */
public record FlagResponse(
        UUID id,
        UUID accountId,
        UUID triggerEventId,
        String ruleId,
        int ruleVersion,
        String modelVersion,
        BigDecimal score,
        List<String> reasons,
        List<UUID> evidenceTransactionIds,
        LocalDateTime asOf,
        LocalDateTime createdAt,
        String status,
        List<DecisionResponse> decisions) {

    public static FlagResponse of(FraudFlag flag, List<AnalystDecision> decisions) {
        List<DecisionResponse> history = decisions.stream().map(DecisionResponse::of).toList();
        String status = history.isEmpty() ? "OPEN" : history.get(history.size() - 1).decision().name();
        return new FlagResponse(flag.getId(), flag.getAccountId(), flag.getTriggerEventId(), flag.getRuleId(),
                flag.getRuleVersion(), flag.getModelVersion(), flag.getScore(), splitReasons(flag.getReasons()),
                splitIds(flag.getEvidenceTransactionIds()), flag.getAsOf(), flag.getCreatedAt(), status, history);
    }

    static List<String> splitReasons(String reasons) {
        return reasons.isEmpty() ? List.of() : List.of(reasons.split("\n"));
    }

    static List<UUID> splitIds(String ids) {
        return ids.isEmpty() ? List.of() : Arrays.stream(ids.split(",")).map(UUID::fromString).toList();
    }
}
