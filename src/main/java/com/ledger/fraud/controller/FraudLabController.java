package com.ledger.fraud.controller;

import com.ledger.fraud.model.AccountFeatures;
import com.ledger.fraud.model.AnomalyModel;
import com.ledger.fraud.model.AnomalyModelRuleV1;
import com.ledger.fraud.service.DemoScenarioService;
import com.ledger.fraud.service.EvaluationService;
import com.ledger.fraud.sim.DetectorEvaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Model facts, measured precision and recall, and the demo scenario. Analysts only (see SecurityConfig). */
@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudLabController {

    private final EvaluationService evaluationService;
    private final DemoScenarioService demoScenarioService;
    private final AnomalyModelRuleV1 anomalyRule;

    // Off in any deployment whose ledger must not receive demo payments.
    @org.springframework.beans.factory.annotation.Value("${traceledger.demo.enabled:true}")
    private boolean demoEnabled;

    @GetMapping("/evaluation")
    public ResponseEntity<DetectorEvaluation.Report> evaluation() {
        return ResponseEntity.ok(evaluationService.report());
    }

    @GetMapping("/model")
    public ResponseEntity<Map<String, Object>> model() {
        AnomalyModel model = anomalyRule.model();
        List<Map<String, Object>> features = new ArrayList<>();
        for (int f = 0; f < AccountFeatures.NAMES.size(); f++) {
            features.add(Map.of("name", AccountFeatures.NAMES.get(f),
                    "typical", AccountFeatures.describe(f, model.typical(f))));
        }
        return ResponseEntity.ok(Map.of(
                "version", model.version(),
                "threshold", model.threshold(),
                "window", "7 days",
                "trees", AnomalyModel.TREES,
                "features", features));
    }

    @PostMapping("/demo/mule-ring")
    public ResponseEntity<DemoScenarioService.ScenarioResult> runMuleRing() {
        if (!demoEnabled) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(demoScenarioService.runMuleRing());
    }
}
