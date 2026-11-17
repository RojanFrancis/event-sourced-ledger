package com.ledger.fraud.model;

import com.ledger.enums.EventType;
import com.ledger.fraud.rules.AccountActivity;
import com.ledger.fraud.rules.MuleRule;
import com.ledger.fraud.rules.RuleContext;
import com.ledger.fraud.rules.RuleHit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * The anomaly model as a detector: when money leaves an account, score the account's last
 * {@link AccountFeatures#WINDOW} of activity and flag it if the score is above the model's threshold.
 * The reasons name the features that drove the score, compared with their typical value.
 */
@Component
public class AnomalyModelRuleV1 implements MuleRule {

    static final int TOP_DRIVERS = 3;
    static final int MAX_EVIDENCE = 25;

    private final AnomalyModel model;

    public AnomalyModelRuleV1() {
        this(AnomalyModel.trainDefault());
    }

    AnomalyModelRuleV1(AnomalyModel model) {
        this.model = model;
    }

    @Override
    public String id() {
        return "ANOMALY_MODEL";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String modelVersion() {
        return model.version();
    }

    @Override
    public Duration lookback() {
        return AccountFeatures.WINDOW;
    }

    public AnomalyModel model() {
        return model;
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext context) {
        AccountActivity trigger = context.trigger();
        if (trigger.type() != EventType.DEBIT) {
            return Optional.empty();
        }
        AccountFeatures features = AccountFeatures.of(context.history(), context.accountCreatedAt(), trigger.at());
        double score = model.score(features);
        if (score < model.threshold()) {
            return Optional.empty();
        }
        double[] share = model.contributions(features);
        double[] raw = features.raw();
        List<String> reasons = new ArrayList<>();
        reasons.add(String.format(Locale.ROOT, "Anomaly score %.2f is above the model threshold %.2f",
                score, model.threshold()));
        IntStream.range(0, share.length).boxed()
                .sorted(Comparator.comparingDouble((Integer f) -> -share[f]).thenComparing(f -> f))
                .limit(TOP_DRIVERS)
                .forEach(f -> reasons.add(String.format(Locale.ROOT, "%s: %s (typical %s), %d%% of the signal",
                        AccountFeatures.NAMES.get(f), AccountFeatures.describe(f, raw[f]),
                        AccountFeatures.describe(f, model.typical(f)), Math.round(share[f] * 100))));

        Set<UUID> evidence = new LinkedHashSet<>();
        List<AccountActivity> history = context.history();
        for (int i = history.size() - 1; i >= 0 && evidence.size() < MAX_EVIDENCE; i--) {
            evidence.add(history.get(i).transactionId());
        }
        List<UUID> ordered = new ArrayList<>(evidence);
        java.util.Collections.reverse(ordered);
        return Optional.of(new RuleHit(BigDecimal.valueOf(score).setScale(4, java.math.RoundingMode.HALF_UP),
                reasons, ordered));
    }
}
