package com.ledger.fraud.rules;

import com.ledger.enums.EventType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Money that leaves soon after it arrives: on a debit, if at least {@link #MIN_INFLOW} came in
 * during the window and at least {@link #PASS_THROUGH_RATIO} of it has gone out in the same window.
 */
@Component
public class RapidPassThroughRuleV1 implements MuleRule {

    static final Duration WINDOW = Duration.ofMinutes(30);
    static final BigDecimal MIN_INFLOW = new BigDecimal("10000");
    static final BigDecimal PASS_THROUGH_RATIO = new BigDecimal("0.80");

    @Override
    public String id() {
        return "RAPID_PASS_THROUGH";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public Duration lookback() {
        return WINDOW;
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext context) {
        AccountActivity trigger = context.trigger();
        if (trigger.type() != EventType.DEBIT) {
            return Optional.empty();
        }
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        Set<UUID> evidence = new LinkedHashSet<>();
        for (AccountActivity activity : context.history()) {
            if (activity.at().isBefore(trigger.at().minus(WINDOW))) {
                continue;
            }
            if (activity.type() == EventType.CREDIT) {
                in = in.add(activity.amount());
            } else {
                out = out.add(activity.amount());
            }
            evidence.add(activity.transactionId());
        }
        if (in.compareTo(MIN_INFLOW) < 0 || out.compareTo(in.multiply(PASS_THROUGH_RATIO)) < 0) {
            return Optional.empty();
        }
        BigDecimal ratio = out.divide(in, 4, RoundingMode.HALF_UP);
        List<String> reasons = new ArrayList<>();
        reasons.add("₹" + in.stripTrailingZeros().toPlainString() + " arrived in the "
                + WINDOW.toMinutes() + " minutes up to this debit");
        reasons.add("₹" + out.stripTrailingZeros().toPlainString() + " ("
                + ratio.movePointRight(2).setScale(0, RoundingMode.HALF_UP) + "%) left in the same window");
        return Optional.of(new RuleHit(ratio.min(BigDecimal.ONE), reasons, List.copyOf(evidence)));
    }
}
