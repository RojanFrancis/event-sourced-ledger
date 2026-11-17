package com.ledger.fraud.rules;

import com.ledger.enums.EventType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MuleRulesTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 12, 20, 0);
    private static final UUID ACCOUNT = UUID.randomUUID();

    private static AccountActivity credit(String amount, LocalDateTime at, UUID from) {
        return new AccountActivity(UUID.randomUUID(), UUID.randomUUID(), EventType.CREDIT, new BigDecimal(amount), at, from);
    }

    private static AccountActivity debit(String amount, LocalDateTime at) {
        return new AccountActivity(UUID.randomUUID(), UUID.randomUUID(), EventType.DEBIT, new BigDecimal(amount), at, UUID.randomUUID());
    }

    private static RuleContext context(LocalDateTime createdAt, List<AccountActivity> history) {
        return new RuleContext(ACCOUNT, createdAt, history.get(history.size() - 1), history);
    }

    @Nested
    class RapidPassThrough {

        private final RapidPassThroughRuleV1 rule = new RapidPassThroughRuleV1();

        @Test
        void flagsMoneyLeavingSoonAfterArriving() {
            AccountActivity in1 = credit("8000", T.minusMinutes(20), UUID.randomUUID());
            AccountActivity in2 = credit("7000", T.minusMinutes(10), UUID.randomUUID());
            AccountActivity out = debit("14000", T);

            RuleHit hit = rule.evaluate(context(T.minusDays(30), List.of(in1, in2, out))).orElseThrow();

            assertThat(hit.score()).isEqualByComparingTo("0.9333");
            assertThat(hit.reasons()).containsExactly(
                    "₹15000 arrived in the 30 minutes up to this debit",
                    "₹14000 (93%) left in the same window");
            assertThat(hit.evidenceTransactionIds())
                    .containsExactly(in1.transactionId(), in2.transactionId(), out.transactionId());
        }

        @Test
        void ignoresInflowOutsideTheWindow() {
            AccountActivity old = credit("15000", T.minusMinutes(31), UUID.randomUUID());
            AccountActivity out = debit("14000", T);

            assertThat(rule.evaluate(context(T.minusDays(30), List.of(old, out)))).isEmpty();
        }

        @Test
        void ignoresSmallInflowAndPartialOutflow() {
            assertThat(rule.evaluate(context(T.minusDays(30),
                    List.of(credit("9000", T.minusMinutes(5), null), debit("9000", T))))).isEmpty();
            assertThat(rule.evaluate(context(T.minusDays(30),
                    List.of(credit("20000", T.minusMinutes(5), null), debit("15000", T))))).isEmpty();
        }

        @Test
        void onlyEvaluatesDebits() {
            assertThat(rule.evaluate(context(T.minusDays(30),
                    List.of(debit("14000", T.minusMinutes(5)), credit("15000", T, null))))).isEmpty();
        }
    }

    @Nested
    class FanInNewAccount {

        private final FanInNewAccountRuleV1 rule = new FanInNewAccountRuleV1();

        private List<AccountActivity> creditsFromDistinctSenders(int senders) {
            List<AccountActivity> history = new ArrayList<>();
            for (int i = senders; i >= 1; i--) {
                history.add(credit("3000", T.minusHours(i), UUID.randomUUID()));
            }
            history.add(credit("3000", T, UUID.randomUUID()));
            return history;
        }

        @Test
        void flagsManySendersIntoANewAccount() {
            List<AccountActivity> history = creditsFromDistinctSenders(4);

            RuleHit hit = rule.evaluate(context(T.minusDays(2), history)).orElseThrow();

            assertThat(hit.score()).isEqualByComparingTo("0.5");
            assertThat(hit.reasons()).containsExactly(
                    "5 different accounts sent money in the last 24 hours",
                    "The account is 2 day(s) old");
            assertThat(hit.evidenceTransactionIds()).hasSize(5);
        }

        @Test
        void countsEachSenderOnce() {
            UUID sender = UUID.randomUUID();
            List<AccountActivity> history = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                history.add(credit("3000", T.minusMinutes(60 - i), sender));
            }
            assertThat(rule.evaluate(context(T.minusDays(1), history))).isEmpty();
        }

        @Test
        void ignoresEstablishedAccounts() {
            assertThat(rule.evaluate(context(T.minusDays(7), creditsFromDistinctSenders(9)))).isEmpty();
        }

        @Test
        void ignoresDepositsAsTrigger() {
            List<AccountActivity> history = creditsFromDistinctSenders(5);
            history.add(credit("1000", T.plusMinutes(1), null));
            assertThat(rule.evaluate(context(T.minusDays(1), history))).isEmpty();
        }
    }
}
