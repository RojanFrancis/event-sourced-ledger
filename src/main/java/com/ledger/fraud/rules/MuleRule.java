package com.ledger.fraud.rules;

import java.time.Duration;
import java.util.Optional;

/**
 * A deterministic mule-account rule. A rule's logic and thresholds never change under the same
 * version: a change is a new class with a new version, and the old one stays registered so that
 * flags it raised can still be replayed.
 */
public interface MuleRule {

    String id();

    int version();

    /** How far back before the trigger the rule looks. */
    Duration lookback();

    Optional<RuleHit> evaluate(RuleContext context);
}
