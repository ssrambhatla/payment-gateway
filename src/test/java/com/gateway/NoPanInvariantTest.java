package com.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.domain.IdempotencyRecord;
import com.gateway.domain.IdempotencyRecordRepository;
import com.gateway.domain.LedgerEntry;
import com.gateway.domain.LedgerEntryRepository;
import com.gateway.domain.Payment;
import com.gateway.domain.PaymentRepository;
import com.gateway.domain.WebhookDeliveryRepository;
import com.gateway.tokenization.TokenVault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * No-PAN invariant: a test card number must never appear in the database,
 * the ledger, or an API response. Only a vault token + last4 are stored.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NoPanInvariantTest {

    private static final String TEST_PAN = "4242424242424242";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private LedgerEntryRepository ledgerEntries;

    @Autowired
    private TokenVault vault;

    @Autowired
    private WebhookDeliveryRepository deliveries;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecords;

    @Test
    void panNeverReachesDatabaseLedgerOrApiResponse() throws Exception {
        String apiKey = createMerchant("No PAN Store").apiKey();

        MvcResult created = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":9900,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isCreated())
                .andReturn();
        String responseBody = created.getResponse().getContentAsString();
        assertFalse(responseBody.contains(TEST_PAN), "API response must not contain the PAN");

        UUID paymentId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());
        Payment stored = payments.findById(paymentId).orElseThrow();

        // Stored payment holds a token, not the PAN; last4 only.
        assertNotNull(stored.getPaymentToken());
        assertTrue(stored.getPaymentToken().startsWith("tok_"));
        assertFalse(stored.getPaymentToken().contains(TEST_PAN));
        assertEquals("4242", stored.getLast4());
        assertFalse(String.valueOf(stored.getPspReference()).contains(TEST_PAN));

        // The entity has no PAN/card-number field at all (compile-level invariant).
        List<String> fieldNames = Arrays.stream(Payment.class.getDeclaredFields())
                .map(Field::getName)
                .map(String::toLowerCase)
                .toList();
        assertFalse(fieldNames.stream().anyMatch(n -> n.contains("pan") || n.contains("cardnumber")),
                "Payment entity must not have a PAN field: " + fieldNames);

        // Ledger entries carry accounts/amounts only — no PAN anywhere.
        for (LedgerEntry entry : ledgerEntries.findByPaymentId(paymentId)) {
            assertFalse(entry.getAccount().contains(TEST_PAN));
        }

        // The token still resolves in the vault (adapter path), proving the
        // mapping exists without the PAN living in the database.
        assertEquals(TEST_PAN, vault.detokenize(stored.getPaymentToken()));
    }

    @Test
    void panNeverReachesWebhookPayloadsOrIdempotencyRecords() throws Exception {
        Merchant merchant = createMerchant("No PAN Webhook Store");
        String apiKey = merchant.apiKey();
        UUID merchantId = merchant.id();

        mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .header("Idempotency-Key", "pan-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":9900,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isCreated());

        // Webhook deliveries (payload + signature) must not contain the PAN.
        MvcResult listed = mockMvc.perform(get("/v1/webhooks/deliveries").header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();
        String deliveriesBody = listed.getResponse().getContentAsString();
        assertFalse(deliveriesBody.contains(TEST_PAN),
                "Webhook delivery payload/signature must not contain the PAN");

        // The idempotency record stores only a one-way hash of the payload —
        // the PAN itself (or anything containing it) is never persisted.
        IdempotencyRecord record = idempotencyRecords
                .findByMerchantIdAndIdempotencyKey(merchantId, "pan-key-1")
                .orElseThrow();
        assertNotEquals(TEST_PAN, record.getRequestHash());
        assertFalse(record.getRequestHash().contains(TEST_PAN),
                "Idempotency hash must not contain the PAN");
        assertFalse(record.getRequestHash().contains("4242"),
                "Idempotency hash must not even contain the card's last4");
    }

    private record Merchant(UUID id, String apiKey) {
    }

    private Merchant createMerchant(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Merchant(UUID.fromString(body.get("merchantId").asText()),
                body.get("apiKey").asText());
    }
}
