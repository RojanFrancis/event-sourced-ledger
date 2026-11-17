package com.ledger.fraud.model;

import java.util.Random;

/**
 * Isolation Forest (Liu, Ting and Zhou, 2008): unusual points are isolated by fewer random splits,
 * so a short average path means a high anomaly score. Training is fully determined by the seed, so
 * the same data and seed always give the same forest, which is what lets a model flag be replayed.
 */
public final class IsolationForest {

    private final Node[] trees;
    private final int sampleSize;
    private final int features;

    private IsolationForest(Node[] trees, int sampleSize, int features) {
        this.trees = trees;
        this.sampleSize = sampleSize;
        this.features = features;
    }

    public static IsolationForest train(double[][] data, int treeCount, int sampleSize, long seed) {
        if (data.length == 0) {
            throw new IllegalArgumentException("No training data");
        }
        Random random = new Random(seed);
        int psi = Math.min(sampleSize, data.length);
        int heightLimit = (int) Math.ceil(Math.log(psi) / Math.log(2));
        Node[] trees = new Node[treeCount];
        for (int t = 0; t < treeCount; t++) {
            double[][] sample = new double[psi][];
            for (int i = 0; i < psi; i++) {
                sample[i] = data[random.nextInt(data.length)];
            }
            trees[t] = build(sample, 0, heightLimit, random);
        }
        return new IsolationForest(trees, psi, data[0].length);
    }

    private static Node build(double[][] points, int depth, int heightLimit, Random random) {
        if (depth >= heightLimit || points.length <= 1) {
            return Node.leaf(points.length);
        }
        int dims = points[0].length;
        double[] min = new double[dims];
        double[] max = new double[dims];
        java.util.Arrays.fill(min, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(max, Double.NEGATIVE_INFINITY);
        for (double[] p : points) {
            for (int f = 0; f < dims; f++) {
                min[f] = Math.min(min[f], p[f]);
                max[f] = Math.max(max[f], p[f]);
            }
        }
        int splittable = 0;
        for (int f = 0; f < dims; f++) {
            if (max[f] > min[f]) {
                splittable++;
            }
        }
        if (splittable == 0) {
            return Node.leaf(points.length);
        }
        // Pick uniformly among features that can still be split.
        int pick = random.nextInt(splittable);
        int feature = -1;
        for (int f = 0; f < dims; f++) {
            if (max[f] > min[f] && pick-- == 0) {
                feature = f;
                break;
            }
        }
        double split = min[feature] + random.nextDouble() * (max[feature] - min[feature]);
        int left = 0;
        for (double[] p : points) {
            if (p[feature] < split) {
                left++;
            }
        }
        double[][] l = new double[left][];
        double[][] r = new double[points.length - left][];
        int li = 0;
        int ri = 0;
        for (double[] p : points) {
            if (p[feature] < split) {
                l[li++] = p;
            } else {
                r[ri++] = p;
            }
        }
        return Node.split(feature, split,
                build(l, depth + 1, heightLimit, random),
                build(r, depth + 1, heightLimit, random));
    }

    /** Anomaly score from 0 to 1; around 0.5 is ordinary, close to 1 is very unusual. */
    public double score(double[] x) {
        double total = 0;
        for (Node tree : trees) {
            total += pathLength(tree, x);
        }
        return Math.pow(2, -(total / trees.length) / averagePath(sampleSize));
    }

    /**
     * How much each feature drove the isolation of {@code x}: every split on the way to x's leaf
     * credits its feature, weighted more when the whole path is short (the point was easy to isolate).
     * The shares add up to 1.
     */
    public double[] contributions(double[] x) {
        double[] share = new double[features];
        for (Node tree : trees) {
            double length = pathLength(tree, x);
            Node node = tree;
            while (!node.isLeaf()) {
                share[node.feature] += 1.0 / length;
                node = x[node.feature] < node.split ? node.left : node.right;
            }
        }
        double sum = 0;
        for (double s : share) {
            sum += s;
        }
        if (sum > 0) {
            for (int f = 0; f < features; f++) {
                share[f] /= sum;
            }
        }
        return share;
    }

    private static double pathLength(Node tree, double[] x) {
        Node node = tree;
        int depth = 0;
        while (!node.isLeaf()) {
            node = x[node.feature] < node.split ? node.left : node.right;
            depth++;
        }
        return depth + averagePath(node.size);
    }

    /** Average path length of an unsuccessful search in a binary search tree of n points. */
    static double averagePath(int n) {
        if (n <= 1) {
            return 0;
        }
        if (n == 2) {
            return 1;
        }
        return 2 * (Math.log(n - 1) + 0.5772156649) - 2.0 * (n - 1) / n;
    }

    private static final class Node {
        final int feature;
        final double split;
        final Node left;
        final Node right;
        final int size;

        private Node(int feature, double split, Node left, Node right, int size) {
            this.feature = feature;
            this.split = split;
            this.left = left;
            this.right = right;
            this.size = size;
        }

        static Node leaf(int size) {
            return new Node(-1, 0, null, null, size);
        }

        static Node split(int feature, double split, Node left, Node right) {
            return new Node(feature, split, left, right, 0);
        }

        boolean isLeaf() {
            return feature < 0;
        }
    }
}
