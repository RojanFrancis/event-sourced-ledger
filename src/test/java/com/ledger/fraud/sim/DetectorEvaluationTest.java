package com.ledger.fraud.sim;

import com.ledger.fraud.model.AnomalyModelRuleV1;
import com.ledger.fraud.rules.FanInNewAccountRuleV1;
import com.ledger.fraud.rules.MuleRule;
import com.ledger.fraud.rules.RapidPassThroughRuleV1;
import com.ledger.fraud.sim.DetectorEvaluation.DetectorResult;
import com.ledger.fraud.sim.TransactionSimulator.SimulatedLedger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the measured baseline on held-out simulated data (the model is trained on seed 7, this uses
 * seed 2026). If a change to a rule, a feature or the simulator moves these numbers, the test says so.
 */
class DetectorEvaluationTest {

    private static DetectorEvaluation.Report report;
    private static Map<String, DetectorResult> byDetector;

    @BeforeAll
    static void evaluate() {
        List<MuleRule> rules = List.of(new FanInNewAccountRuleV1(), new RapidPassThroughRuleV1(), new AnomalyModelRuleV1());
        report = DetectorEvaluation.run(TransactionSimulator.generate(2026, 5000), rules);
        byDetector = report.detectors().stream().collect(Collectors.toMap(DetectorResult::detector, Function.identity()));
    }

    @Test
    void theSimulationIsReproducible() {
        SimulatedLedger a = TransactionSimulator.generate(11, 500);
        SimulatedLedger b = TransactionSimulator.generate(11, 500);
        assertThat(a.accounts()).isEqualTo(b.accounts());
        assertThat(a.activity()).isEqualTo(b.activity());
        assertThat(TransactionSimulator.generate(12, 500).accounts()).isNotEqualTo(a.accounts());
    }

    @Test
    void aboutTwoAndAHalfPercentOfAccountsAreMules() {
        assertThat(report.mules()).isBetween(100L, 140L);
        assertThat(report.accounts()).isEqualTo(5000);
    }

    @Test
    void eachRuleCatchesThePatternItWasWrittenFor() {
        assertThat(byDetector.get("RAPID_PASS_THROUGH v1").recallByPattern())
                .containsEntry("MULE_PASS_THROUGH", 1.0).containsEntry("MULE_LAYERING", 1.0);
        assertThat(byDetector.get("FAN_IN_NEW_ACCOUNT v1").recallByPattern())
                .containsEntry("MULE_COLLECTOR", 1.0);
    }

    @Test
    void rulesOnlyMisfireOnTheLookAlikesTheSimulatorPlants() {
        assertThat(byDetector.get("RAPID_PASS_THROUGH v1").falsePositivesByPattern()).containsOnlyKeys("SALARIED");
        assertThat(byDetector.get("FAN_IN_NEW_ACCOUNT v1").falsePositivesByPattern()).containsOnlyKeys("NEW_CUSTOMER");
    }

    @Test
    void theModelMeetsItsBaseline() {
        DetectorResult model = byDetector.get("ANOMALY_MODEL v1");
        assertThat(model.recall()).isGreaterThanOrEqualTo(0.80);
        assertThat(model.precision()).isGreaterThanOrEqualTo(0.35);
        assertThat(model.modelVersion()).startsWith("iforest-v1");
    }

    @Test
    void combinedTheDetectorsCatchAllButTheSlowMules() {
        DetectorResult all = report.combined();
        assertThat(all.recall()).isGreaterThanOrEqualTo(0.85);
        assertThat(all.recallByPattern()).containsEntry("MULE_COLLECTOR", 1.0).containsEntry("MULE_LAYERING", 1.0);
        // A known gap, kept visible on purpose: slow pass-through below every threshold.
        assertThat(all.recallByPattern().get("MULE_SLOW")).isLessThan(0.5);
    }
}
