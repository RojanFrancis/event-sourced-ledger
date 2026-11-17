package com.ledger.fraud.rules;

import java.time.Duration;

/** Shared by live screening and the offline evaluation, so both raise flags the same way. */
public final class FlagPolicy {

    /**
     * After a detector flags an account, it does not flag the same account again for this long.
     * One open case per pattern is what an analyst needs, not one per payment.
     */
    public static final Duration COOLDOWN = Duration.ofHours(1);

    private FlagPolicy() {
    }
}
