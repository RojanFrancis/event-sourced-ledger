package com.ledger.fraud.sim;

import com.ledger.enums.EventType;
import com.ledger.fraud.rules.AccountActivity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generates a labelled synthetic ledger: ordinary customers, ordinary customers who look suspicious
 * (salary swept to savings at once, a new account receiving gifts from friends, merchants with many
 * payers), and planted mule accounts of four kinds. Everything comes from one seed, so a run can be
 * repeated exactly, and training and evaluation use different seeds.
 */
public final class TransactionSimulator {

    public enum Pattern {
        SALARIED(false, "Salaried customer"),
        STUDENT(false, "Student or low-activity customer"),
        MERCHANT(false, "Small merchant"),
        NEW_CUSTOMER(false, "New customer"),
        MULE_PASS_THROUGH(true, "Mule: rapid pass-through"),
        MULE_COLLECTOR(true, "Mule: new account collecting from many victims"),
        MULE_LAYERING(true, "Mule: link in a layering chain"),
        MULE_SLOW(true, "Mule: slow pass-through below rule thresholds");

        public final boolean mule;
        public final String label;

        Pattern(boolean mule, String label) {
            this.mule = mule;
            this.label = label;
        }
    }

    public record SimulatedAccount(UUID id, LocalDateTime createdAt, Pattern pattern) {
    }

    /** Accounts with their activity, oldest first per account. */
    public record SimulatedLedger(long seed, LocalDateTime start, int days,
                                  List<SimulatedAccount> accounts,
                                  Map<UUID, List<AccountActivity>> activity) {

        public long muleCount() {
            return accounts.stream().filter(a -> a.pattern().mule).count();
        }

        public int eventCount() {
            return activity.values().stream().mapToInt(List::size).sum();
        }
    }

    public static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 0, 0);
    public static final int DAYS = 14;

    private final Random random;
    private final List<SimulatedAccount> accounts = new ArrayList<>();
    private final Map<UUID, List<AccountActivity>> activity = new HashMap<>();
    private final UUID[] employers;
    private final UUID[] shops;

    private TransactionSimulator(long seed) {
        this.random = new Random(seed);
        this.employers = pool(40);
        this.shops = pool(400);
    }

    public static SimulatedLedger generate(long seed, int accountCount) {
        TransactionSimulator sim = new TransactionSimulator(seed);
        sim.populate(accountCount);
        Map<UUID, List<AccountActivity>> sorted = new LinkedHashMap<>();
        for (SimulatedAccount account : sim.accounts) {
            List<AccountActivity> list = new ArrayList<>(sim.activity.getOrDefault(account.id(), List.of()));
            list.sort(Comparator.comparing(AccountActivity::at));
            sorted.put(account.id(), List.copyOf(list));
        }
        return new SimulatedLedger(seed, START, DAYS, List.copyOf(sim.accounts), sorted);
    }

    private void populate(int n) {
        int passThrough = Math.max(1, Math.round(n * 0.008f));
        int collectors = Math.max(1, Math.round(n * 0.007f));
        int chains = Math.max(1, Math.round(n * 0.0015f));
        int slow = Math.max(1, Math.round(n * 0.003f));
        int merchants = Math.round(n * 0.13f);
        int students = Math.round(n * 0.25f);
        int newCustomers = Math.round(n * 0.17f);
        int planted = passThrough + collectors + chains * 4 + slow;
        int salaried = Math.max(0, n - planted - merchants - students - newCustomers);

        for (int i = 0; i < salaried; i++) salaried();
        for (int i = 0; i < students; i++) student();
        for (int i = 0; i < merchants; i++) merchant();
        for (int i = 0; i < newCustomers; i++) newCustomer();
        for (int i = 0; i < passThrough; i++) passThroughMule();
        for (int i = 0; i < collectors; i++) collectorMule();
        for (int i = 0; i < chains; i++) layeringChain();
        for (int i = 0; i < slow; i++) slowMule();
    }

    // ── Ordinary customers ──────────────────────────────────────────────────

    private void salaried() {
        SimulatedAccount a = open(Pattern.SALARIED, START.minusDays(between(30, 1500)));
        LocalDateTime payday = day(between(0, DAYS - 2)).plusHours(between(9, 11)).plusMinutes(between(0, 59));
        long salary = roundTo(between(25_000, 150_000), 100);
        credit(a, payday, salary, pick(employers));
        if (chance(0.04)) {
            // Sweeps most of the salary to the customer's own savings elsewhere straight away.
            debit(a, payday.plusMinutes(between(5, 28)), Math.round(salary * between(80, 95) / 100.0), uuid());
        } else {
            debit(a, payday.plusDays(between(1, 3)).plusHours(between(0, 12)), Math.round(salary * between(25, 35) / 100.0), uuid());
        }
        for (int d = 0; d < DAYS; d++) {
            int spends = between(0, 3);
            for (int s = 0; s < spends; s++) {
                debit(a, day(d).plusHours(between(8, 22)).plusMinutes(between(0, 59)), between(100, 4000), pick(shops));
            }
        }
        int friends = between(0, 2);
        for (int f = 0; f < friends; f++) {
            credit(a, day(between(0, DAYS - 1)).plusHours(between(8, 22)), between(200, 3000), uuid());
        }
    }

    private void student() {
        SimulatedAccount a = open(Pattern.STUDENT, START.minusDays(between(30, 1500)));
        UUID[] family = pool(between(1, 2));
        int credits = between(1, 3);
        for (int c = 0; c < credits; c++) {
            credit(a, day(between(0, DAYS - 1)).plusHours(between(8, 21)), roundTo(between(2_000, 15_000), 500), pick(family));
        }
        int spends = between(5, 25);
        for (int s = 0; s < spends; s++) {
            debit(a, day(between(0, DAYS - 1)).plusHours(between(8, 23)).plusMinutes(between(0, 59)), between(50, 1500), pick(shops));
        }
    }

    private void merchant() {
        SimulatedAccount a = open(Pattern.MERCHANT, START.minusDays(between(60, 2000)));
        UUID settlement = uuid();
        for (int d = 0; d < DAYS; d++) {
            long takings = 0;
            int customers = between(5, 40);
            for (int c = 0; c < customers; c++) {
                long amount = between(100, 3000);
                takings += amount;
                credit(a, day(d).plusHours(between(8, 21)).plusMinutes(between(0, 59)), amount, uuid());
            }
            debit(a, day(d).plusHours(23).plusMinutes(between(0, 30)), Math.round(takings * between(70, 95) / 100.0), settlement);
        }
    }

    private void newCustomer() {
        LocalDateTime opened = day(between(0, DAYS - 3)).plusHours(between(9, 18));
        SimulatedAccount a = open(Pattern.NEW_CUSTOMER, opened);
        credit(a, opened.plusMinutes(between(5, 120)), roundTo(between(1_000, 20_000), 500), null);
        if (chance(0.08)) {
            // A birthday or a shared bill: friends send small amounts on the same day.
            LocalDateTime party = opened.plusDays(between(0, 2)).plusHours(between(1, 6));
            int friends = between(5, 9);
            for (int f = 0; f < friends; f++) {
                credit(a, party.plusMinutes(between(0, 360)), roundTo(between(500, 3_000), 100), uuid());
            }
        }
        if (chance(0.2)) {
            credit(a, opened.plusDays(between(1, 2)).plusHours(10), roundTo(between(20_000, 60_000), 100), pick(employers));
        }
        int spends = between(2, 15);
        for (int s = 0; s < spends; s++) {
            LocalDateTime at = opened.plusHours(between(3, 24 * 4)).plusMinutes(between(0, 59));
            debit(a, at, between(100, 2500), pick(shops));
        }
    }

    // ── Mule accounts ───────────────────────────────────────────────────────

    private void passThroughMule() {
        SimulatedAccount a = open(Pattern.MULE_PASS_THROUGH, START.minusDays(between(3, 90)));
        int episodes = between(2, 4);
        for (int e = 0; e < episodes; e++) {
            LocalDateTime t = day(between(0, DAYS - 1)).plusHours(between(0, 23)).plusMinutes(between(0, 59));
            long in = 0;
            int payers = between(1, 2);
            for (int p = 0; p < payers; p++) {
                long amount = roundTo(between(20_000, 200_000), 1000);
                in += amount;
                credit(a, t.plusMinutes(p * between(1, 4)), amount, uuid());
            }
            forward(a, t.plusMinutes(between(3, 25)), Math.round(in * between(85, 98) / 100.0), between(1, 3), 3);
        }
        for (int s = between(0, 5); s > 0; s--) {
            debit(a, day(between(0, DAYS - 1)).plusHours(between(9, 22)), between(100, 1500), pick(shops));
        }
    }

    private void collectorMule() {
        LocalDateTime opened = day(between(0, DAYS - 3)).plusHours(between(8, 20));
        SimulatedAccount a = open(Pattern.MULE_COLLECTOR, opened);
        LocalDateTime t = opened.plusHours(between(1, 24));
        long in = 0;
        int victims = between(6, 18);
        for (int v = 0; v < victims; v++) {
            t = t.plusMinutes(between(5, 60));
            long amount = roundTo(between(2_000, 25_000), 100);
            in += amount;
            credit(a, t, amount, uuid());
        }
        forward(a, t.plusMinutes(between(60, 360)), Math.round(in * between(88, 97) / 100.0), between(1, 3), 20);
    }

    private void layeringChain() {
        int length = 4;
        List<SimulatedAccount> chain = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            chain.add(open(Pattern.MULE_LAYERING, START.minusDays(between(1, 20))));
        }
        LocalDateTime t = day(between(0, DAYS - 2)).plusHours(between(0, 23));
        long amount = roundTo(between(50_000, 300_000), 1000);
        credit(chain.get(0), t, amount, uuid());
        for (int i = 0; i < length; i++) {
            t = t.plusMinutes(between(3, 15));
            long next = Math.round(amount * between(92, 97) / 100.0);
            UUID to = i + 1 < length ? chain.get(i + 1).id() : uuid();
            UUID tx = uuid();
            add(chain.get(i).id(), tx, EventType.DEBIT, next, t, to);
            if (i + 1 < length) {
                add(to, tx, EventType.CREDIT, next, t, chain.get(i).id());
            }
            amount = next;
        }
    }

    private void slowMule() {
        SimulatedAccount a = open(Pattern.MULE_SLOW, START.minusDays(between(10, 120)));
        LocalDateTime t = day(between(0, DAYS - 4)).plusHours(between(8, 20));
        int payers = 4;
        for (int p = 0; p < payers; p++) {
            long amount = roundTo(between(10_000, 40_000), 500);
            credit(a, t, amount, uuid());
            debit(a, t.plusMinutes(between(180, 480)), Math.round(amount * between(88, 95) / 100.0), uuid());
            t = t.plusHours(between(10, 20));
        }
    }

    /** Sends {@code amount} on in {@code parts} payments a few minutes apart, to new accounts outside the sample. */
    private void forward(SimulatedAccount from, LocalDateTime at, long amount, int parts, int maxGapMinutes) {
        long left = amount;
        for (int p = 0; p < parts; p++) {
            long part = p == parts - 1 ? left : Math.round(amount / (double) parts);
            left -= part;
            debit(from, at.plusMinutes((long) p * between(1, maxGapMinutes)), part, chance(0.3) ? null : uuid());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private SimulatedAccount open(Pattern pattern, LocalDateTime createdAt) {
        SimulatedAccount account = new SimulatedAccount(uuid(), createdAt, pattern);
        accounts.add(account);
        return account;
    }

    private void credit(SimulatedAccount account, LocalDateTime at, long amount, UUID from) {
        add(account.id(), uuid(), EventType.CREDIT, amount, at, from);
    }

    private void debit(SimulatedAccount account, LocalDateTime at, long amount, UUID to) {
        add(account.id(), uuid(), EventType.DEBIT, amount, at, to);
    }

    private void add(UUID account, UUID tx, EventType type, long amount, LocalDateTime at, UUID counterparty) {
        activity.computeIfAbsent(account, k -> new ArrayList<>())
                .add(new AccountActivity(uuid(), tx, type, BigDecimal.valueOf(amount), at, counterparty));
    }

    // UUIDs come from the seeded generator too, so a whole run is reproducible.
    private UUID uuid() {
        return new UUID(random.nextLong(), random.nextLong());
    }

    private UUID[] pool(int size) {
        UUID[] ids = new UUID[size];
        for (int i = 0; i < size; i++) {
            ids[i] = new UUID(random.nextLong(), random.nextLong());
        }
        return ids;
    }

    private LocalDateTime day(int d) {
        return START.plusDays(d);
    }

    private int between(int lo, int hi) {
        return lo + random.nextInt(hi - lo + 1);
    }

    private boolean chance(double p) {
        return random.nextDouble() < p;
    }

    private <T> T pick(T[] items) {
        return items[random.nextInt(items.length)];
    }

    private static long roundTo(long value, long step) {
        return Math.max(step, Math.round(value / (double) step) * step);
    }
}
