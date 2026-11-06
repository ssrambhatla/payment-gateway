package com.gateway;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.domain.LedgerEntry;
import com.gateway.domain.LedgerEntryRepository;
import com.gateway.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

/**
 * M2 FX: the rate is locked at authorize time and carried on the payment and
 * the ledger entries (presentment + settlement currency + applied rate).
 */
@SpringBootTest
@AutoConfigureMockMvc
class FxTest {

    private static final String TEST_PAN = "4242424242424242";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LedgerEntryRepository ledgerEntries;

    @Autowired
    private LedgerService ledger;

    @Test
    void eurPaymentLocksRateAndSettlementAmountAtAuthorizeTime() throws Exception {
        String apiKey = createMerchant("FX Store");

        // €50.00, settlement USD. Mock rate EUR->USD is 1/0.92 = 1.086957.
        MvcResult created = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":5000,"currency":"EUR","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.settlementCurrency").value("USD"))
                .andExpect(jsonPath("$.fxRate", closeTo(1.086957, 0.000001)))
                .andExpect(jsonPath("$.settlementAmountMinor").value(5435))
                .andReturn();
        UUID paymentId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        // Ledger entries carry the same FX facts and still balance.
        List<LedgerEntry> entries = ledgerEntries.findByPaymentId(paymentId);
        assertEquals(2, entries.size());
        for (LedgerEntry entry : entries) {
            assertEquals("USD", entry.getSettlementCurrency());
            assertEquals(0, entry.getFxRate().compareTo(new java.math.BigDecimal("1.086957")));
            assertEquals(5435L, entry.getSettlementAmountMinor());
        }
        ledger.assertBalanced(paymentId);
    }

    @Test
    void usdPaymentHasUnitRateAndSameSettlementAmount() throws Exception {
        String apiKey = createMerchant("USD FX Store");

        mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":5000,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fxRate", closeTo(1.0, 0.000001)))
                .andExpect(jsonPath("$.settlementAmountMinor").value(5000));
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
