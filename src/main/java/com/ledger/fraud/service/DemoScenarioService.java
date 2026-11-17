package com.ledger.fraud.service;

import com.ledger.entity.Account;
import com.ledger.entity.User;
import com.ledger.enums.AccountType;
import com.ledger.fraud.entity.FraudFlag;
import com.ledger.fraud.repository.FraudFlagRepository;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.UserRepository;
import com.ledger.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a mule scenario through the real ledger, for demos: payments go through the normal
 * {@link TransactionService}, so screening, flags and replay behave exactly as for real customers.
 * Every account it creates belongs to one system user, so demo data never mixes with real accounts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoScenarioService {

    static final String SIMULATOR_EMAIL = "simulator@traceledger.invalid";

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final FraudFlagRepository flagRepository;
    private final PasswordEncoder passwordEncoder;

    public record ScenarioAccount(UUID id, String name, String role) {
    }

    public record ScenarioFlag(UUID flagId, String ruleId, String accountRole, BigDecimal score) {
    }

    public record ScenarioResult(List<ScenarioAccount> accounts, int transfers, List<ScenarioFlag> flags,
                                 List<String> story) {
    }

    /**
     * Eight people pay a brand-new account, which passes the money down a two-hop chain and out as cash,
     * while an ordinary customer is paid a salary and pays bills alongside.
     */
    public ScenarioResult runMuleRing() {
        User owner = simulatorUser();
        Map<UUID, String> roles = new LinkedHashMap<>();
        List<ScenarioAccount> accounts = new ArrayList<>();
        List<String> story = new ArrayList<>();
        int[] transfers = {0};

        Account salaried = open(owner, "Ordinary customer", 900, roles, accounts);
        Account employer = open(owner, "Employer", 2000, roles, accounts);
        Account mule = open(owner, "Mule (new account)", 0, roles, accounts);
        Account hop1 = open(owner, "Layering hop 1", 5, roles, accounts);
        Account hop2 = open(owner, "Layering hop 2", 12, roles, accounts);
        List<Account> victims = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            victims.add(open(owner, "Payer " + i, ThreadLocalRandom.current().nextInt(200, 3000), roles, accounts));
        }

        transactionService.deposit(employer.getId(), new BigDecimal("500000"), "Company funds", key());
        transactionService.transfer(employer.getId(), salaried.getId(), new BigDecimal("48000"), "Salary", key());
        transactionService.transfer(salaried.getId(), employer.getId(), new BigDecimal("1200"), "Electricity bill", key());
        transactionService.transfer(salaried.getId(), employer.getId(), new BigDecimal("650"), "Mobile recharge", key());
        transfers[0] += 3;
        story.add("An ordinary customer is paid ₹48,000 and pays two bills: nothing should be flagged.");

        BigDecimal collected = BigDecimal.ZERO;
        for (Account victim : victims) {
            BigDecimal amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(40, 121) * 100L);
            transactionService.deposit(victim.getId(), new BigDecimal("20000"), "Own savings", key());
            transactionService.transfer(victim.getId(), mule.getId(), amount, "Payment for online order", key());
            collected = collected.add(amount);
            transfers[0]++;
        }
        story.add("8 different people pay the new account " + inr(collected) + " in total.");

        BigDecimal first = percent(collected, 92);
        transactionService.transfer(mule.getId(), hop1.getId(), first, "Transfer", key());
        BigDecimal second = percent(first, 96);
        transactionService.transfer(hop1.getId(), hop2.getId(), second, "Transfer", key());
        BigDecimal cash = percent(second, 95);
        transactionService.withdraw(hop2.getId(), cash, "Cash withdrawal", key());
        transfers[0] += 3;
        story.add("Within seconds 92% moves on to a second account, then a third, and " + inr(cash)
                + " leaves as cash.");

        List<ScenarioFlag> flags = new ArrayList<>();
        for (UUID id : roles.keySet()) {
            for (FraudFlag flag : flagRepository.findByAccountIdOrderByCreatedAtDesc(id)) {
                flags.add(new ScenarioFlag(flag.getId(), flag.getRuleId(), roles.get(id), flag.getScore()));
            }
        }
        log.info("Demo mule ring played: {} transfers, {} flags", transfers[0], flags.size());
        return new ScenarioResult(accounts, transfers[0], flags, story);
    }

    private Account open(User owner, String role, int ageDays, Map<UUID, String> roles,
                         List<ScenarioAccount> accounts) {
        String name = "Customer " + ThreadLocalRandom.current().nextInt(1000, 10000);
        Account account = accountRepository.save(Account.builder()
                .ownerName(name).userId(owner.getId()).accountType(AccountType.SAVINGS).build());
        if (ageDays > 0) {
            accountRepository.backdate(account.getId(), LocalDateTime.now().minusDays(ageDays));
        }
        roles.put(account.getId(), role);
        accounts.add(new ScenarioAccount(account.getId(), name, role));
        return account;
    }

    private User simulatorUser() {
        return userRepository.findByEmail(SIMULATOR_EMAIL).orElseGet(() -> userRepository.save(User.builder()
                .fullName("TraceLedger simulator")
                .email(SIMULATOR_EMAIL)
                // Nobody can sign in as the simulator: the password is random and never kept.
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("ROLE_USER")
                .build()));
    }

    private static BigDecimal percent(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN);
    }

    private static String inr(BigDecimal amount) {
        return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.of("en", "IN"))
                .format(amount).replace(".00", "");
    }

    private static String key() {
        return "demo-" + UUID.randomUUID();
    }
}
