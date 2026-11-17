package com.ledger.fraud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Analysts are named in traceledger.analyst-emails; everyone else is kept out of /api/fraud. */
@SpringBootTest
@AutoConfigureMockMvc
class AnalystAccessTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper json = new ObjectMapper();

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!x\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    @Test
    void aConfiguredAnalystCanOpenTheFraudScreen() throws Exception {
        String token = register("Lead.Analyst@bank.test");
        mockMvc.perform(get("/api/fraud/model").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Lead.Analyst@bank.test\",\"password\":\"Passw0rd!x\"}"))
                .andExpect(jsonPath("$.analyst").value(true));
    }

    @Test
    void anOrdinaryCustomerCannot() throws Exception {
        String token = register("someone@bank.test");
        mockMvc.perform(get("/api/fraud/model").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/fraud/demo/mule-ring").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
