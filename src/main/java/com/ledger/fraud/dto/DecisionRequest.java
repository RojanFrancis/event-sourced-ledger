package com.ledger.fraud.dto;

import com.ledger.fraud.entity.Decision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionRequest(@NotNull Decision decision, @Size(max = 1000) String note) {
}
