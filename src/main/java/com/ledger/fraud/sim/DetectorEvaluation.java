package com.ledger.fraud.sim;

import com.ledger.fraud.rules.FlagPolicy;
import com.ledger.fraud.rules.MuleRule;
import com.ledger.fraud.sim.TransactionSimulator.Pattern;
import com.ledger.fraud.sim.TransactionSimulator.SimulatedAccount;
import com.ledger.fraud.sim.TransactionSimulator.SimulatedLedger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the detectors over a labelled simulated ledger, exactly as live screening would (every event,
 * the same lookback, the same cooldown), and measures them per account: an account counts as caught
 * if any flag was raised on it.
 */
public final class DetectorEvaluation {

    public record DetectorResult(
            String detector,
            String modelVersion,
            int flags,
            int flaggedAccounts,
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            Map<String, Double> recallByPattern,
            Map<String, Integer> falsePositivesByPattern) {
    }

    public record Report(
            long seed,
            int accounts,
            long mules,
            int events,
            int days,
            Map<String, Integer> accountsByPattern,
            List<DetectorResult> detectors,
            DetectorResult combined,
            long millis) {
    }

    private DetectorEvaluation() {
    }

    public static Report run(SimulatedLedger ledger, List<MuleRule> rules) {
        long started = System.nanoTime();
        Duration lookback = rules.stream().map(MuleRule::lookback).max(Duration::compareTo).orElse(Duration.ZERO);

        Map<String, Set<UUID>> flaggedBy = new LinkedHashMap<>();
        Map<String, Integer> flagCount = new HashMap<>();
        Map<String, LocalDateTime> lastFlag = new HashMap<>();
        rules.forEach(r -> flaggedBy.put(key(r), new HashSet<>()));

        SimulatedContexts.forEach(ledger, lookback, (account, context) -> {
            for (MuleRule rule : rules) {
                String cooldownKey = key(rule) + "|" + account.id();
                LocalDateTime previous = lastFlag.get(cooldownKey);
                if (previous != null && context.trigger().at().isBefore(previous.plus(FlagPolicy.COOLDOWN))) {
                    continue;
                }
                if (rule.evaluate(context).isPresent()) {
                    lastFlag.put(cooldownKey, context.trigger().at());
                    flaggedBy.get(key(rule)).add(account.id());
                    flagCount.merge(key(rule), 1, Integer::sum);
                }
            }
        });

        Map<UUID, Pattern> patterns = new HashMap<>();
        Map<String, Integer> byPattern = new LinkedHashMap<>();
        for (SimulatedAccount account : ledger.accounts()) {
            patterns.put(account.id(), account.pattern());
            byPattern.merge(account.pattern().name(), 1, Integer::sum);
        }

        List<DetectorResult> results = new ArrayList<>();
        Set<UUID> any = new HashSet<>();
        int totalFlags = 0;
        for (MuleRule rule : rules) {
            Set<UUID> flagged = flaggedBy.get(key(rule));
            any.addAll(flagged);
            int flags = flagCount.getOrDefault(key(rule), 0);
            totalFlags += flags;
            results.add(measure(key(rule), rule.modelVersion(), flags, flagged, ledger, patterns));
        }
        DetectorResult combined = measure("ALL", null, totalFlags, any, ledger, patterns);
        return new Report(ledger.seed(), ledger.accounts().size(), ledger.muleCount(), ledger.eventCount(),
                ledger.days(), byPattern, results, combined, (System.nanoTime() - started) / 1_000_000);
    }

    private static DetectorResult measure(String detector, String modelVersion, int flags, Set<UUID> flagged,
                                          SimulatedLedger ledger, Map<UUID, Pattern> patterns) {
        int tp = 0;
        int fp = 0;
        int fn = 0;
        Map<Pattern, int[]> perPattern = new EnumMap<>(Pattern.class);
        Map<String, Integer> fpByPattern = new LinkedHashMap<>();
        for (SimulatedAccount account : ledger.accounts()) {
            boolean hit = flagged.contains(account.id());
            boolean mule = account.pattern().mule;
            if (mule) {
                int[] counts = perPattern.computeIfAbsent(account.pattern(), p -> new int[2]);
                counts[1]++;
                if (hit) {
                    counts[0]++;
                    tp++;
                } else {
                    fn++;
                }
            } else if (hit) {
                fp++;
                fpByPattern.merge(account.pattern().name(), 1, Integer::sum);
            }
        }
        Map<String, Double> recallByPattern = new LinkedHashMap<>();
        perPattern.forEach((p, c) -> recallByPattern.put(p.name(), ratio(c[0], c[1])));
        return new DetectorResult(detector, modelVersion, flags, flagged.size(), tp, fp, fn,
                ratio(tp, tp + fp), ratio(tp, tp + fn), recallByPattern, fpByPattern);
    }

    private static double ratio(int a, int b) {
        return b == 0 ? 0 : Math.round(1000.0 * a / b) / 1000.0;
    }

    private static String key(MuleRule rule) {
        return rule.id() + " v" + rule.version();
    }
}
