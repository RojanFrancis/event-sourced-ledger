package com.ledger.fraud.rules;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Everything a rule may look at, all as of the trigger event.
 *
 * @param history the account's activity in the lookback window, oldest first, ending with the trigger.
 *                Nothing recorded after the trigger is included, so evaluating the same context later
 *                (a replay) gives the same answer.
 */
public record RuleContext(
        UUID accountId,
        LocalDateTime accountCreatedAt,
        AccountActivity trigger,
        List<AccountActivity> history) {
}
