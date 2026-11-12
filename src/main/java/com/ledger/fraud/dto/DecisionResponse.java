package com.ledger.fraud.dto;

import com.ledger.fraud.entity.AnalystDecision;
import com.ledger.fraud.entity.Decision;

import java.time.LocalDateTime;
import java.util.UUID;

public record DecisionResponse(UUID id, Decision decision, String analyst, String note,
                               String ruleId, int ruleVersion, String modelVersion, LocalDateTime createdAt) {

    public static DecisionResponse of(AnalystDecision d) {
        return new DecisionResponse(d.getId(), d.getDecision(), d.getAnalyst(), d.getNote(),
                d.getRuleId(), d.getRuleVersion(), d.getModelVersion(), d.getCreatedAt());
    }
}
