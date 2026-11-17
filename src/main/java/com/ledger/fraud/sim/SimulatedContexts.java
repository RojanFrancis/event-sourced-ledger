package com.ledger.fraud.sim;

import com.ledger.fraud.rules.AccountActivity;
import com.ledger.fraud.rules.RuleContext;
import com.ledger.fraud.sim.TransactionSimulator.SimulatedAccount;
import com.ledger.fraud.sim.TransactionSimulator.SimulatedLedger;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Builds the same {@link RuleContext} the live screening builds, but from a simulated ledger in
 * memory: one context per event, holding that account's activity in the lookback window up to and
 * including the event. Detectors therefore run unchanged on simulated and real data.
 */
public final class SimulatedContexts {

    private SimulatedContexts() {
    }

    public static void forEach(SimulatedLedger ledger, Duration lookback,
                               BiConsumer<SimulatedAccount, RuleContext> consumer) {
        for (SimulatedAccount account : ledger.accounts()) {
            List<AccountActivity> events = ledger.activity().get(account.id());
            int from = 0;
            for (int i = 0; i < events.size(); i++) {
                AccountActivity trigger = events.get(i);
                while (events.get(from).at().isBefore(trigger.at().minus(lookback))) {
                    from++;
                }
                consumer.accept(account, new RuleContext(account.id(), account.createdAt(), trigger,
                        events.subList(from, i + 1)));
            }
        }
    }
}
