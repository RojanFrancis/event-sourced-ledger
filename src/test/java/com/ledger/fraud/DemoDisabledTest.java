package com.ledger.fraud;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A deployment can switch the demo off so its ledger never receives demo payments. */
@SpringBootTest(properties = "traceledger.demo.enabled=false")
@AutoConfigureMockMvc
@WithMockUser(username = "analyst@bank.test", authorities = "ROLE_ANALYST")
class DemoDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theDemoScenarioIsNotAvailable() throws Exception {
        mockMvc.perform(post("/api/fraud/demo/mule-ring")).andExpect(status().isNotFound());
    }
}
