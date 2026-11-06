package com.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** M2 refunds: partial/full, ledger balance, and the refund guards. */
@SpringBootTest
@AutoConfigureMockMvc
class RefundTest {

    private static final String TEST_PAN = "4242424242424242";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LedgerService ledger;

    @Test
    void partialThenFullRefundMovesThroughBothStates() throws Exception {
        String apiKey = createMerchant("Refund Store");
        UUID paymentId = createAndCapture(apiKey, 5000);

        // Partial refund of $20.00 -> PARTIALLY_REFUNDED.
        mockMvc.perform(post("/v1/payments/" + paymentId + "/refund")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(2000))
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
        mockMvc.perform(get("/v1/payments/" + paymentId).header("X-Api-Key", apiKey))
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
        ledger.assertBalanced(paymentId);
        assertEquals(2000, ledger.refundedAmountMinor(paymentId));

        // Refund the remaining $30.00 -> REFUNDED.
        mockMvc.perform(post("/v1/payments/" + paymentId + "/refund")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(get("/v1/payments/" + paymentId).header("X-Api-Key", apiKey))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        ledger.assertBalanced(paymentId);
        assertEquals(5000, ledger.refundedAmountMinor(paymentId));

        // A fully refunded payment is terminal: another refund is a 409.
        mockMvc.perform(post("/v1/payments/" + paymentId + "/refund")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":100}"))
                .andExpect(status().isConflict());
    }

    @Test
    void emptyBodyRefundsTheFullRemainingBalance() throws Exception {
        String apiKey = createMerchant("Full Refund Store");
        UUID paymentId = createAndCapture(apiKey, 2500);

        mockMvc.perform(post("/v1/payments/" + paymentId + "/refund")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(2500))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        ledger.assertBalanced(paymentId);
    }

    @Test
    void refundBeforeCaptureIsAConflict() throws Exception {
        String apiKey = createMerchant("Early Refund Store");
        UUID paymentId = createAuthorized(apiKey, 5000);

        mockMvc.perform(post("/v1/payments/" + paymentId + "/refund")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":1000}"))
                .andExpect(status().isConflict());
    }

    @Test
    void refundAboveTheRefundableBalanceIsRejected() throws Exception {
        String apiKey = createMerchant("Over Refund Store");
        UUID paymentId = createAndCapture(apiKey, 5000);

        mockMvc.perform(post("/v1/payments/" + paymentId + "/refund")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":5001}"))
                .andExpect(status().isBadRequest());
    }

    private UUID createAuthorized(String apiKey, long amountMinor) throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":%d,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(amountMinor, TEST_PAN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createAndCapture(String apiKey, long amountMinor) throws Exception {
        UUID paymentId = createAuthorized(apiKey, amountMinor);
        mockMvc.perform(post("/v1/payments/" + paymentId + "/capture").header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
        return paymentId;
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
