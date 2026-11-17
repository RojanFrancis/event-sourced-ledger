package com.ledger.fraud.model;

import com.ledger.enums.EventType;
import com.ledger.fraud.sim.SimulatedContexts;
import com.ledger.fraud.sim.TransactionSimulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A trained Isolation Forest with its alert threshold and the typical value of each feature, which
 * the explanation compares against. It is trained without labels on a simulated ledger; the seeds and
 * sizes are part of {@link #version()}, so a flag records exactly which model raised it, and the same
 * version always rebuilds the same model.
 */
public final class AnomalyModel {

    public static final long TRAINING_SEED = 7;
    public static final int TRAINING_ACCOUNTS = 3000;
    public static final long FOREST_SEED = 42;
    public static final int TREES = 100;
    public static final int SAMPLE_SIZE = 256;
    /** Share of training snapshots scored above the threshold. */
    public static final double ALERT_RATE = 0.01;

    private final IsolationForest forest;
    private final double threshold;
    private final double[] typical;
    private final String version;

    private AnomalyModel(IsolationForest forest, double threshold, double[] typical, String version) {
        this.forest = forest;
        this.threshold = threshold;
        this.typical = typical;
        this.version = version;
    }

    public static AnomalyModel trainDefault() {
        TransactionSimulator.SimulatedLedger ledger =
                TransactionSimulator.generate(TRAINING_SEED, TRAINING_ACCOUNTS);
        List<AccountFeatures> snapshots = new ArrayList<>();
        SimulatedContexts.forEach(ledger, AccountFeatures.WINDOW, (account, context) -> {
            if (context.trigger().type() == EventType.DEBIT) {
                snapshots.add(AccountFeatures.of(context.history(), context.accountCreatedAt(), context.trigger().at()));
            }
        });
        double[][] inputs = snapshots.stream().map(AccountFeatures::modelInput).toArray(double[][]::new);
        IsolationForest forest = IsolationForest.train(inputs, TREES, SAMPLE_SIZE, FOREST_SEED);

        double[] scores = Arrays.stream(inputs).mapToDouble(forest::score).sorted().toArray();
        double threshold = round4(scores[(int) Math.floor((1 - ALERT_RATE) * (scores.length - 1))]);

        double[] typical = new double[AccountFeatures.NAMES.size()];
        for (int f = 0; f < typical.length; f++) {
            int feature = f;
            double[] column = snapshots.stream().mapToDouble(s -> s.raw()[feature]).sorted().toArray();
            typical[f] = column[column.length / 2];
        }
        String version = "iforest-v1:t" + TREES + "-s" + SAMPLE_SIZE + "-seed" + FOREST_SEED
                + "-alert" + ALERT_RATE + ":sim" + TRAINING_SEED + "x" + TRAINING_ACCOUNTS;
        return new AnomalyModel(forest, threshold, typical, version);
    }

    public double score(AccountFeatures features) {
        return round4(forest.score(features.modelInput()));
    }

    public double[] contributions(AccountFeatures features) {
        return forest.contributions(features.modelInput());
    }

    public double threshold() {
        return threshold;
    }

    public double typical(int feature) {
        return typical[feature];
    }

    public String version() {
        return version;
    }

    static double round4(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
