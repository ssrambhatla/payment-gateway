package com.gateway;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M1 happy path: merchant onboarding → payment create (→ authorized)
 * → capture (→ captured), ledger balanced at each step.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentHappyPathTest {

    private static final String TEST_PAN = "4242424242424242";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LedgerService ledger;

    @Test
    void cardPaymentGoesFromCreateToAuthorizedToCaptured() throws Exception {
        String apiKey = createMerchant("Happy Path Store");

        MvcResult created = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":5000,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.pspReference", startsWith("mock_psp_")))
                .andExpect(jsonPath("$.last4").value("4242"))
                .andExpect(content().string(not(containsString(TEST_PAN))))
                .andReturn();
        UUID paymentId = UUID.fromString(objectMapper
                .readTree(created.getResponse().getContentAsString()).get("id").asText());

        // Ledger balances after authorization.
        ledger.assertBalanced(paymentId);

        // Status query returns the same payment.
        mockMvc.perform(get("/v1/payments/" + paymentId).header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));

        // Capture moves AUTHORIZED -> CAPTURED, ledger still balanced.
        mockMvc.perform(post("/v1/payments/" + paymentId + "/capture").header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
        ledger.assertBalanced(paymentId);

        // Second capture is an invalid transition -> 409.
        mockMvc.perform(post("/v1/payments/" + paymentId + "/capture").header("X-Api-Key", apiKey))
                .andExpect(status().isConflict());
    }

    @Test
    void achAndIbpAdaptersAuthorizeToo() throws Exception {
        String apiKey = createMerchant("Bank Methods Store");

        mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"ACH","amountMinor":12000,"currency":"USD","paymentDetails":"mock-bank-acct-001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));

        mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"IBP","amountMinor":7500,"currency":"USD","paymentDetails":"mock-bank-ref-001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));
    }

    @Test
    void unknownApiKeyIsRejected() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", "sk_test_does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":100,"currency":"USD","paymentDetails":"4242424242424242"}
                                """))
                .andExpect(status().isUnauthorized());
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
