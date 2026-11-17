package com.ledger.fraud.service;

import com.ledger.fraud.rules.RuleRegistry;
import com.ledger.fraud.sim.DetectorEvaluation;
import com.ledger.fraud.sim.TransactionSimulator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Precision and recall of the active detectors on a held-out simulated ledger (a different seed
 * from the one the model was trained on). Computed once on first request and kept, since the
 * detectors cannot change while the application runs.
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final RuleRegistry ruleRegistry;

    @Value("${traceledger.evaluation.seed:2026}")
    private long seed;

    @Value("${traceledger.evaluation.accounts:5000}")
    private int accounts;

    private volatile DetectorEvaluation.Report report;

    public DetectorEvaluation.Report report() {
        DetectorEvaluation.Report current = report;
        if (current == null) {
            synchronized (this) {
                if (report == null) {
                    report = DetectorEvaluation.run(TransactionSimulator.generate(seed, accounts), ruleRegistry.active());
                }
                current = report;
            }
        }
        return current;
    }
}
