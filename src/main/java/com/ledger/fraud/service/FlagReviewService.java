package com.ledger.fraud.service;

import com.ledger.exception.FlagNotFoundException;
import com.ledger.exception.RuleVersionUnavailableException;
import com.ledger.fraud.dto.DecisionRequest;
import com.ledger.fraud.dto.FlagResponse;
import com.ledger.fraud.dto.ReplayResponse;
import com.ledger.fraud.entity.AnalystDecision;
import com.ledger.fraud.entity.FraudFlag;
import com.ledger.fraud.repository.AnalystDecisionRepository;
import com.ledger.fraud.repository.FraudFlagRepository;
import com.ledger.fraud.rules.MuleRule;
import com.ledger.fraud.rules.RuleContext;
import com.ledger.fraud.rules.RuleHit;
import com.ledger.fraud.rules.RuleRegistry;
import com.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlagReviewService {

    private final FraudFlagRepository flagRepository;
    private final AnalystDecisionRepository decisionRepository;
    private final RuleRegistry ruleRegistry;
    private final RuleContextBuilder contextBuilder;
    private final LedgerService ledgerService;

    @Transactional(readOnly = true)
    public List<FlagResponse> recentFlags() {
        List<FraudFlag> flags = flagRepository.findTop200ByOrderByCreatedAtDesc();
        Map<UUID, List<AnalystDecision>> decisions = decisionRepository
                .findByFlagIdInOrderByCreatedAtAsc(flags.stream().map(FraudFlag::getId).toList())
                .stream().collect(Collectors.groupingBy(AnalystDecision::getFlagId));
        return flags.stream().map(f -> FlagResponse.of(f, decisions.getOrDefault(f.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public FlagResponse getFlag(UUID flagId) {
        return FlagResponse.of(load(flagId), decisionRepository.findByFlagIdOrderByCreatedAtAsc(flagId));
    }

    @Transactional
    public FlagResponse decide(UUID flagId, DecisionRequest request, String analyst) {
        FraudFlag flag = load(flagId);
        decisionRepository.save(AnalystDecision.builder()
                .flagId(flagId)
                .decision(request.decision())
                .analyst(analyst)
                .note(request.note())
                .ruleId(flag.getRuleId())
                .ruleVersion(flag.getRuleVersion())
                .modelVersion(flag.getModelVersion())
                .build());
        return FlagResponse.of(flag, decisionRepository.findByFlagIdOrderByCreatedAtAsc(flagId));
    }

    /** Rebuilds the flag's decision from the ledger as of the moment it was raised. */
    @Transactional(readOnly = true)
    public ReplayResponse replay(UUID flagId) {
        FraudFlag flag = load(flagId);
        MuleRule rule = ruleRegistry.find(flag.getRuleId(), flag.getRuleVersion())
                .orElseThrow(() -> new RuleVersionUnavailableException(
                        "Rule " + flag.getRuleId() + " v" + flag.getRuleVersion() + " is not available to replay"));
        RuleContext context = contextBuilder.build(flag.getTriggerEventId(), rule.lookback());
        RuleHit hit = rule.evaluate(context).orElse(null);

        FlagResponse original = FlagResponse.of(flag, List.of());
        BigDecimal replayedScore = hit == null ? null : hit.score();
        List<String> replayedReasons = hit == null ? List.of() : hit.reasons();
        List<UUID> replayedEvidence = hit == null ? List.of() : hit.evidenceTransactionIds();
        boolean matches = hit != null
                && replayedScore.compareTo(flag.getScore()) == 0
                && replayedReasons.equals(original.reasons())
                && replayedEvidence.equals(original.evidenceTransactionIds());

        return new ReplayResponse(flag.getId(), flag.getRuleId(), flag.getRuleVersion(), flag.getAsOf(), matches,
                flag.getScore(), replayedScore, original.reasons(), replayedReasons,
                original.evidenceTransactionIds(), replayedEvidence, context.history().size(),
                ledgerService.computeBalanceAsOf(flag.getAccountId(), flag.getAsOf()));
    }

    private FraudFlag load(UUID flagId) {
        return flagRepository.findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException("Flag not found: " + flagId));
    }
}
