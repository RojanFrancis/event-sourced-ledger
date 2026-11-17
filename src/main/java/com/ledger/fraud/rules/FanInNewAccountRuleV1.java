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
 * Many unrelated senders paying a new account: on an incoming transfer to an account younger than
 * {@link #NEW_ACCOUNT_AGE}, if at least {@link #MIN_SENDERS} different accounts sent it money
 * within the window.
 */
@Component
public class FanInNewAccountRuleV1 implements MuleRule {

    static final Duration WINDOW = Duration.ofHours(24);
    static final Duration NEW_ACCOUNT_AGE = Duration.ofDays(7);
    static final int MIN_SENDERS = 5;
    private static final int SENDERS_FOR_FULL_SCORE = 10;

    @Override
    public String id() {
        return "FAN_IN_NEW_ACCOUNT";
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
        if (trigger.type() != EventType.CREDIT || trigger.counterpartyAccountId() == null
                || context.accountCreatedAt() == null) {
            return Optional.empty();
        }
        Duration age = Duration.between(context.accountCreatedAt(), trigger.at());
        if (age.compareTo(NEW_ACCOUNT_AGE) >= 0) {
            return Optional.empty();
        }
        Set<UUID> senders = new LinkedHashSet<>();
        Set<UUID> evidence = new LinkedHashSet<>();
        for (AccountActivity activity : context.history()) {
            if (activity.type() == EventType.CREDIT && activity.counterpartyAccountId() != null
                    && !activity.at().isBefore(trigger.at().minus(WINDOW))) {
                senders.add(activity.counterpartyAccountId());
                evidence.add(activity.transactionId());
            }
        }
        if (senders.size() < MIN_SENDERS) {
            return Optional.empty();
        }
        BigDecimal score = BigDecimal.valueOf(senders.size())
                .divide(BigDecimal.valueOf(SENDERS_FOR_FULL_SCORE), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        List<String> reasons = new ArrayList<>();
        reasons.add(senders.size() + " different accounts sent money in the last " + WINDOW.toHours() + " hours");
        reasons.add("The account is " + describeAge(age) + " old");
        return Optional.of(new RuleHit(score, reasons, List.copyOf(evidence)));
    }

    private static String describeAge(Duration age) {
        return age.toDays() >= 1 ? age.toDays() + " day(s)" : age.toHours() + " hour(s)";
    }
}
