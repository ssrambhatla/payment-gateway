package com.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.domain.LedgerEntry;
import com.gateway.domain.LedgerEntryRepository;
import com.gateway.ledger.LedgerService;
import com.gateway.settlement.SettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** M2 ACH + IBP settlement: ACH settles after the batch delay, IBP instantly. */
@SpringBootTest
@AutoConfigureMockMvc
class AchIbpSettlementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettlementService settlement;

    @Autowired
    private LedgerEntryRepository ledgerEntries;

    @Autowired
    private LedgerService ledger;

    @Test
    void achCaptureStaysPendingThenSettlesAfterTheBatchJob() throws Exception {
        String apiKey = createMerchant("ACH Store");
        UUID paymentId = createAndCapture(apiKey, "ACH", "mock-bank-account-123");

        // After capture: CAPTURED with a settlement due date (batch model).
        MvcResult captured = mockMvc.perform(get("/v1/payments/" + paymentId).header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andReturn();
        assertNotNull(objectMapper.readTree(captured.getResponse().getContentAsString())
                        .get("settlementDueAt").asText(),
                "ACH capture must stamp a settlement due date");

        // The batch job settles it (test config: delay 0ms, so it is due now).
        settlement.settleDuePayments();
        mockMvc.perform(get("/v1/payments/" + paymentId).header("X-Api-Key", apiKey))
                .andExpect(jsonPath("$.status").value("SETTLED"));
        ledger.assertBalanced(paymentId);
        assertEquals(2, ledgerEntries
                .findByPaymentIdAndEntryType(paymentId, LedgerEntry.EntryType.SETTLE).size(),
                "Settlement posts one SETTLE debit + one SETTLE credit");
    }

    @Test
    void ibpCaptureSettlesImmediately() throws Exception {
        String apiKey = createMerchant("IBP Store");
        UUID paymentId = createAndCapture(apiKey, "IBP", "mock-bank-ref-456");

        // IBP capture returns SETTLED right away — no batch wait.
        mockMvc.perform(get("/v1/payments/" + paymentId).header("X-Api-Key", apiKey))
                .andExpect(jsonPath("$.status").value("SETTLED"));
        ledger.assertBalanced(paymentId);
        assertEquals(2, ledgerEntries
                .findByPaymentIdAndEntryType(paymentId, LedgerEntry.EntryType.SETTLE).size(),
                "Settlement posts one SETTLE debit + one SETTLE credit");
    }

    private UUID createAndCapture(String apiKey, String method, String details) throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"%s","amountMinor":5000,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(method, details)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andReturn();
        UUID paymentId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/v1/payments/" + paymentId + "/capture").header("X-Api-Key", apiKey))
                .andExpect(status().isOk());
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
