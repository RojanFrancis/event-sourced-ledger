package com.ledger.fraud.model;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IsolationForestTest {

    private static double[][] cluster(long seed, int n) {
        Random random = new Random(seed);
        double[][] data = new double[n][];
        for (int i = 0; i < n; i++) {
            data[i] = new double[]{random.nextGaussian(), random.nextGaussian(), random.nextGaussian()};
        }
        return data;
    }

    @Test
    void anOutlierScoresHigherThanAnOrdinaryPoint() {
        IsolationForest forest = IsolationForest.train(cluster(1, 2000), 100, 256, 42);
        double ordinary = forest.score(new double[]{0, 0, 0});
        double offOnOneFeature = forest.score(new double[]{0, 9, 0});
        double offOnAll = forest.score(new double[]{9, 9, 9});
        assertThat(ordinary).isLessThan(0.45);
        assertThat(offOnOneFeature).isGreaterThan(ordinary + 0.1);
        assertThat(offOnAll).isGreaterThan(0.7);
    }

    @Test
    void contributionsPointAtTheFeatureThatIsOff() {
        IsolationForest forest = IsolationForest.train(cluster(1, 2000), 100, 256, 42);
        double[] share = forest.contributions(new double[]{0, 9, 0});
        assertThat(share[0] + share[1] + share[2]).isCloseTo(1.0, within(1e-9));
        assertThat(share[1]).isGreaterThan(share[0]).isGreaterThan(share[2]).isGreaterThan(0.35);
    }

    @Test
    void theSameSeedAlwaysBuildsTheSameForest() {
        double[][] data = cluster(3, 1000);
        IsolationForest a = IsolationForest.train(data, 50, 128, 7);
        IsolationForest b = IsolationForest.train(data, 50, 128, 7);
        for (double[] point : cluster(9, 50)) {
            assertThat(a.score(point)).isEqualTo(b.score(point));
        }
    }
}
