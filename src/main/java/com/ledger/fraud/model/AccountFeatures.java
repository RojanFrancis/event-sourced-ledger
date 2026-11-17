package com.ledger.fraud.model;

import com.ledger.enums.EventType;
import com.ledger.fraud.rules.AccountActivity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What the anomaly model sees about one account, as of one event, over the {@link #WINDOW} before it.
 * Values are in plain units (rupees, minutes, days) so they can be shown to an analyst as they are;
 * {@link #modelInput()} is the scaled form the model is trained and scored on.
 */
public record AccountFeatures(
        double moneyIn,
        double passedOnShare,
        int distinctSenders,
        double holdingMinutes,
        double accountAgeDays) {

    public static final Duration WINDOW = Duration.ofDays(7);

    /** Holding time used when no money left, or none had arrived before it left. */
    static final double NO_PASS_THROUGH_MINUTES = WINDOW.toMinutes();

    public static final List<String> NAMES = List.of(
            "Money received",
            "Share passed on",
            "Different senders",
            "Time money stays",
            "Account age");

    /**
     * @param history the account's activity in the window, oldest first, ending with the trigger.
     *                Only events at or before {@code asOf} are used, so the result is reproducible.
     */
    public static AccountFeatures of(List<AccountActivity> history, LocalDateTime accountCreatedAt,
                                     LocalDateTime asOf) {
        LocalDateTime from = asOf.minus(WINDOW);
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        Set<UUID> senders = new HashSet<>();
        List<double[]> holds = new ArrayList<>();
        LocalDateTime lastCredit = null;
        for (AccountActivity activity : history) {
            if (activity.at().isBefore(from) || activity.at().isAfter(asOf)) {
                continue;
            }
            if (activity.type() == EventType.CREDIT) {
                in = in.add(activity.amount());
                if (activity.counterpartyAccountId() != null) {
                    senders.add(activity.counterpartyAccountId());
                }
                lastCredit = activity.at();
            } else {
                out = out.add(activity.amount());
                double minutes = lastCredit == null ? NO_PASS_THROUGH_MINUTES
                        : Duration.between(lastCredit, activity.at()).toSeconds() / 60.0;
                holds.add(new double[]{minutes, activity.amount().doubleValue()});
            }
        }
        double moneyIn = in.doubleValue();
        double share = moneyIn == 0 ? 0 : Math.min(out.doubleValue() / moneyIn, 2.0);
        double age = accountCreatedAt == null ? 365
                : Math.max(0, Duration.between(accountCreatedAt, asOf).toSeconds() / 86_400.0);
        return new AccountFeatures(moneyIn, share, senders.size(), weightedMedian(holds), Math.min(age, 365));
    }

    /**
     * The holding time below which half of the money that left had left: weighted by amount, so a
     * large transfer counts for more than many small purchases.
     */
    private static double weightedMedian(List<double[]> holds) {
        if (holds.isEmpty()) {
            return NO_PASS_THROUGH_MINUTES;
        }
        List<double[]> sorted = new ArrayList<>(holds);
        sorted.sort((a, b) -> Double.compare(a[0], b[0]));
        double total = 0;
        for (double[] h : sorted) {
            total += h[1];
        }
        double running = 0;
        for (double[] h : sorted) {
            running += h[1];
            if (running >= total / 2) {
                return h[0];
            }
        }
        return sorted.get(sorted.size() - 1)[0];
    }

    public double[] raw() {
        return new double[]{moneyIn, passedOnShare, distinctSenders, holdingMinutes, accountAgeDays};
    }

    /** Log-scaled where values span orders of magnitude, so a split means the same at every scale. */
    public double[] modelInput() {
        return new double[]{
                Math.log1p(moneyIn),
                passedOnShare,
                Math.log1p(distinctSenders),
                Math.log1p(holdingMinutes),
                Math.log1p(accountAgeDays)};
    }

    /** One feature's value as an analyst would read it. */
    public static String describe(int feature, double value) {
        return switch (feature) {
            case 0 -> "₹" + Math.round(value);
            case 1 -> Math.round(value * 100) + "%";
            case 2 -> String.valueOf(Math.round(value));
            case 3 -> describeMinutes(value);
            case 4 -> value < 1 ? "under a day" : value >= 365 ? "over a year" : Math.round(value) + " days";
            default -> String.valueOf(value);
        };
    }

    private static String describeMinutes(double minutes) {
        if (minutes >= NO_PASS_THROUGH_MINUTES) {
            return "nothing passed on";
        }
        if (minutes < 60) {
            return Math.round(minutes) + " min";
        }
        if (minutes < 48 * 60) {
            return Math.round(minutes / 60) + " h";
        }
        return Math.round(minutes / 1440) + " days";
    }
}
