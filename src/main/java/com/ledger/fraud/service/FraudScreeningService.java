package com.ledger.fraud.service;

import com.ledger.fraud.entity.FraudFlag;
import com.ledger.fraud.repository.FraudFlagRepository;
import com.ledger.fraud.rules.MuleRule;
import com.ledger.fraud.rules.RuleContext;
import com.ledger.fraud.rules.RuleHit;
import com.ledger.fraud.rules.RuleRegistry;
import com.ledger.service.LedgerEventRecorded;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Screens each ledger event with the active rules once the payment has committed. Screening only
 * raises flags for an analyst; it never blocks or reverses a payment, and a failure here is logged
 * rather than surfaced to the payer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudScreeningService {

    private final RuleRegistry ruleRegistry;
    private final RuleContextBuilder contextBuilder;
    private final FraudFlagRepository flagRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLedgerEventRecorded(LedgerEventRecorded recorded) {
        try {
            screen(recorded.ledgerEventId());
        } catch (RuntimeException e) {
            log.error("Fraud screening failed for ledger event {}: {}", recorded.ledgerEventId(), e.getMessage(), e);
        }
    }

    @Transactional
    public List<FraudFlag> screen(UUID ledgerEventId) {
        RuleContext context = contextBuilder.build(ledgerEventId, ruleRegistry.maxLookback());
        List<FraudFlag> raised = new ArrayList<>();
        for (MuleRule rule : ruleRegistry.active()) {
            if (flagRepository.existsByTriggerEventIdAndRuleIdAndRuleVersion(ledgerEventId, rule.id(), rule.version())) {
                continue;
            }
            rule.evaluate(context).ifPresent(hit -> raised.add(flagRepository.save(toFlag(context, rule, hit))));
        }
        raised.forEach(flag -> log.info("Flag raised: rule={} v{} account={} score={}",
                flag.getRuleId(), flag.getRuleVersion(), flag.getAccountId(), flag.getScore()));
        return raised;
    }

    private static FraudFlag toFlag(RuleContext context, MuleRule rule, RuleHit hit) {
        return FraudFlag.builder()
                .accountId(context.accountId())
                .triggerEventId(context.trigger().eventId())
                .ruleId(rule.id())
                .ruleVersion(rule.version())
                .score(hit.score())
                .reasons(String.join("\n", hit.reasons()))
                .evidenceTransactionIds(hit.evidenceTransactionIds().stream()
                        .map(UUID::toString).collect(Collectors.joining(",")))
                .asOf(context.trigger().at())
                .build();
    }
}
