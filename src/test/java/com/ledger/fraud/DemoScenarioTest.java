package com.ledger.fraud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The demo mule ring, played through the real ledger: who gets flagged, and every flag replays exactly. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@WithMockUser(username = "analyst@bank.test", authorities = "ROLE_ANALYST")
class DemoScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void theMuleRingIsFlaggedAndOrdinaryCustomersAreNot() throws Exception {
        String body = mockMvc.perform(post("/api/fraud/demo/mule-ring"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(body);

        Map<String, Set<String>> rulesByRole = new HashMap<>();
        for (JsonNode flag : result.get("flags")) {
            rulesByRole.computeIfAbsent(flag.get("accountRole").asText(), k -> new HashSet<>())
                    .add(flag.get("ruleId").asText());
            mockMvc.perform(get("/api/fraud/flags/{id}/replay", flag.get("flagId").asText()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matches").value(true));
        }
        System.out.println("Demo flags by role: " + rulesByRole);

        assertThat(rulesByRole.get("Mule (new account)"))
                .contains("FAN_IN_NEW_ACCOUNT", "RAPID_PASS_THROUGH", "ANOMALY_MODEL");
        assertThat(rulesByRole.get("Layering hop 1")).contains("RAPID_PASS_THROUGH");
        assertThat(rulesByRole.get("Layering hop 2")).contains("RAPID_PASS_THROUGH");
        assertThat(rulesByRole.keySet())
                .doesNotContain("Ordinary customer", "Employer")
                .noneMatch(role -> role.startsWith("Payer"));
    }

    @Test
    void theModelAndEvaluationAreReported() throws Exception {
        mockMvc.perform(get("/api/fraud/model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(org.hamcrest.Matchers.startsWith("iforest-v1")))
                .andExpect(jsonPath("$.features.length()").value(5));
        mockMvc.perform(get("/api/fraud/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").value(1500))
                .andExpect(jsonPath("$.detectors.length()").value(3))
                .andExpect(jsonPath("$.combined.recall").value(org.hamcrest.Matchers.greaterThan(0.8)));
    }
}
