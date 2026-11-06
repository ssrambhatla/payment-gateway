package com.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.adapters.MockPspAdapter;
import com.gateway.core.PspCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M2 failover drill: kill the primary PSP (mock kill switch) and prove the
 * payment still authorizes — through the secondary PSP. Then restore the
 * primary and prove traffic returns to it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FailoverDrillTest {

    private static final String TEST_PAN = "4242424242424242";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("mockPspAdapter")
    private MockPspAdapter primary;

    @Autowired
    private PspCircuitBreaker breaker;

    @Test
    void killingPrimaryRoutesTrafficToSecondaryAndBack() throws Exception {
        String apiKey = createMerchant("Failover Drill Store");

        try {
            primary.setSimulateFailure(true);
            breaker.resetAll();

            // Primary is dead: the payment must still authorize, via mock-psp-2.
            MvcResult failedOver = createPayment(apiKey);
            String reference = objectMapper.readTree(failedOver.getResponse().getContentAsString())
                    .get("pspReference").asText();
            assertTrue(reference.startsWith("mock2_psp_"),
                    "With the primary killed, traffic must go to the secondary, got: " + reference);
        } finally {
            primary.setSimulateFailure(false);
            breaker.resetAll();
        }

        // Primary is back: traffic returns to it.
        MvcResult restored = createPayment(apiKey);
        String reference = objectMapper.readTree(restored.getResponse().getContentAsString())
                .get("pspReference").asText();
        assertTrue(reference.startsWith("mock_psp_")
                        && !reference.startsWith("mock2_psp_"),
                "With the primary restored, traffic must return to it, got: " + reference);
    }

    @Test
    void repeatedPrimaryFailuresOpenTheCircuitBreaker() throws Exception {
        String apiKey = createMerchant("Breaker Drill Store");
        try {
            primary.setSimulateFailure(true);
            breaker.resetAll();

            // Three authorizations: each fails on the primary and fails over.
            for (int i = 0; i < 3; i++) {
                createPayment(apiKey);
            }
            assertTrue(breaker.state("mock-psp") == PspCircuitBreaker.State.OPEN,
                    "3 consecutive primary failures must open its breaker");
        } finally {
            primary.setSimulateFailure(false);
            breaker.resetAll();
        }
    }

    private MvcResult createPayment(String apiKey) throws Exception {
        return mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":5000,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andReturn();
    }

    private String createMerchant(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("apiKey").asText();
    }
}
