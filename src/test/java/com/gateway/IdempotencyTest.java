package com.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.domain.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** M2 idempotency: same key + same payload replays; same key + new payload 409s. */
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyTest {

    private static final String TEST_PAN = "4242424242424242";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LedgerEntryRepository ledgerEntries;

    @Test
    void sameKeySamePayloadReplaysOriginalPaymentWithoutDoubleCharge() throws Exception {
        String apiKey = createMerchant("Idempotency Store");
        String body = """
                {"method":"CARD","amountMinor":5000,"currency":"USD","paymentDetails":"%s"}
                """.formatted(TEST_PAN);

        MvcResult first = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .header("Idempotency-Key", "idem-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andReturn();
        UUID paymentId = UUID.fromString(
                objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText());

        // Retry with the same key + payload: 200, same payment, no new charge.
        MvcResult replay = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .header("Idempotency-Key", "idem-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andReturn();

        // No duplicate ledger entries: exactly the 2 AUTH entries of the first charge.
        assertEquals(2, ledgerEntries.findByPaymentId(paymentId).size(),
                "Replay must not post duplicate ledger entries");
    }

    @Test
    void sameKeyDifferentPayloadIsAConflict() throws Exception {
        String apiKey = createMerchant("Idempotency Conflict Store");

        mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .header("Idempotency-Key", "idem-conflict-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":5000,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isCreated());

        // Same key, different amount -> 409.
        mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .header("Idempotency-Key", "idem-conflict-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":6000,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isConflict());
    }

    @Test
    void differentKeysCreateDifferentPayments() throws Exception {
        String apiKey = createMerchant("Idempotency Keys Store");
        String body = """
                {"method":"CARD","amountMinor":1000,"currency":"USD","paymentDetails":"%s"}
                """.formatted(TEST_PAN);

        MvcResult first = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .header("Idempotency-Key", "idem-key-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .header("Idempotency-Key", "idem-key-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        UUID firstId = UUID.fromString(
                objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText());
        UUID secondId = UUID.fromString(
                objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText());
        assertNotEquals(firstId, secondId, "Different keys must create different payments");
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
