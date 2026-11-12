package com.ledger.fraud;

import com.ledger.entity.Account;
import com.ledger.enums.AccountType;
import com.ledger.fraud.entity.FraudFlag;
import com.ledger.fraud.repository.FraudFlagRepository;
import com.ledger.repository.AccountRepository;
import com.ledger.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end on an in-memory database: five senders pay a new account, which passes most of the
 * money on. Both rules flag it after the payments commit, each flag replays to the same result,
 * and an analyst decision is appended.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TraceLedgerFlowTest {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private FraudFlagRepository flagRepository;
    @Autowired
    private MockMvc mockMvc;

    private Account account(String owner) {
        return accountRepository.save(Account.builder()
                .ownerName(owner).userId(UUID.randomUUID()).accountType(AccountType.SAVINGS).build());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    @WithMockUser(username = "analyst@bank.test", authorities = "ROLE_ANALYST")
    void flagsAMuleAccountAndReplaysEachFlagExactly() throws Exception {
        Account mule = account("Mule");
        Account exit = account("Exit");
        List<Account> senders = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Account sender = account("Sender " + i);
            transactionService.deposit(sender.getId(), new BigDecimal("5000"), "salary", key());
            senders.add(sender);
        }
        for (Account sender : senders) {
            transactionService.transfer(sender.getId(), mule.getId(), new BigDecimal("3000"), "payment", key());
        }
        transactionService.transfer(mule.getId(), exit.getId(), new BigDecimal("14000"), "cash out", key());

        Map<String, FraudFlag> flags = flagRepository.findAll().stream()
                .collect(Collectors.toMap(FraudFlag::getRuleId, f -> f));
        assertThat(flags).containsOnlyKeys("FAN_IN_NEW_ACCOUNT", "RAPID_PASS_THROUGH");
        assertThat(flags.values()).allMatch(f -> f.getAccountId().equals(mule.getId()));

        // Activity after the flags must not change what a replay reproduces.
        transactionService.deposit(mule.getId(), new BigDecimal("50000"), "later deposit", key());
        transactionService.transfer(senders.get(0).getId(), mule.getId(), new BigDecimal("1000"), "later", key());

        FraudFlag passThrough = flags.get("RAPID_PASS_THROUGH");
        mockMvc.perform(get("/api/fraud/flags/{id}/replay", passThrough.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").value(true))
                .andExpect(jsonPath("$.replayedScore").value(0.9333))
                .andExpect(jsonPath("$.balanceAsOf").value(1000.0))
                .andExpect(jsonPath("$.replayedEvidence.length()").value(6));

        mockMvc.perform(get("/api/fraud/flags/{id}/replay", flags.get("FAN_IN_NEW_ACCOUNT").getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").value(true))
                .andExpect(jsonPath("$.replayedReasons[0]").value("5 different accounts sent money in the last 24 hours"));

        mockMvc.perform(post("/api/fraud/flags/{id}/decisions", passThrough.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"HOLD\",\"note\":\"Pass-through within minutes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HOLD"))
                .andExpect(jsonPath("$.decisions[0].analyst").value("analyst@bank.test"))
                .andExpect(jsonPath("$.decisions[0].ruleVersion").value(1));

        // The later incoming transfer is screened too: five distinct senders are still within 24 hours.
        mockMvc.perform(get("/api/fraud/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].ruleId").value("FAN_IN_NEW_ACCOUNT"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    @WithMockUser(username = "customer@bank.test", authorities = "ROLE_USER")
    void customersCannotSeeFlags() throws Exception {
        mockMvc.perform(get("/api/fraud/flags")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "analyst@bank.test", authorities = "ROLE_ANALYST")
    void unknownFlagIsNotFound() throws Exception {
        mockMvc.perform(get("/api/fraud/flags/{id}/replay", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
