package com.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.core.GatewayProperties;
import com.gateway.domain.WebhookDelivery;
import com.gateway.domain.WebhookDeliveryRepository;
import com.gateway.webhook.HmacSigner;
import com.gateway.webhook.WebhookDeliveryService;
import com.gateway.webhook.WebhookDispatcher;
import com.gateway.webhook.WebhookTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M2 signed webhooks: HMAC signatures verify, a failing transport marks the
 * delivery FAILED with a backoff time, and a manual retry after recovery
 * delivers it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebhookDeliveryTest {

    private static final String TEST_PAN = "4242424242424242";

    /** Controllable transport: the real one in production, this stub in tests. */
    static class StubTransport implements WebhookTransport {
        volatile boolean fail = false;

        @Override
        public boolean deliver(WebhookDelivery delivery) {
            return !fail;
        }
    }

    @TestConfiguration
    static class StubTransportConfig {
        @Bean
        @Primary
        StubTransport stubTransport() {
            return new StubTransport();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookTransport transport;

    @Autowired
    private WebhookDeliveryRepository deliveries;

    @Autowired
    private WebhookDispatcher dispatcher;

    @Autowired
    private WebhookDeliveryService deliveryService;

    @Autowired
    private GatewayProperties properties;

    @Autowired
    private HmacSigner signer;

    @BeforeEach
    void resetTransport() {
        ((StubTransport) transport).fail = false;
    }

    @Test
    void deliveredWebhookIsSignedAndSignatureVerifies() throws Exception {
        String apiKey = createMerchant("Webhook Store").apiKey();
        UUID paymentId = createPayment(apiKey);

        MvcResult list = mockMvc.perform(get("/v1/webhooks/deliveries").header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(list.getResponse().getContentAsString());
        assertTrue(items.size() >= 1, "At least one delivery should exist for the payment");
        JsonNode delivery = items.get(0);
        assertEquals("SENT", delivery.get("status").asText());
        assertEquals(1, delivery.get("attempts").asInt());
        assertEquals("payment.authorized", delivery.get("event").asText());
        assertFalse(delivery.get("payload").asText().contains(TEST_PAN),
                "Webhook payload must never contain a PAN");

        // The signature must verify against the payload it was computed over.
        String signature = delivery.get("signature").asText();
        String[] parts = signature.split(",", 2);
        assertEquals(2, parts.length);
        String timestamp = parts[0].substring("t=".length());
        String expectedV1 = signer.sign(properties.webhookSecret(),
                timestamp + "." + delivery.get("payload").asText());
        assertEquals("v1=" + expectedV1, parts[1], "HMAC signature must verify");

        // Delivery ids are merchant-scoped: a different merchant sees nothing.
        String otherKey = createMerchant("Other Webhook Store").apiKey();
        mockMvc.perform(get("/v1/webhooks/deliveries").header("X-Api-Key", otherKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void failedDeliveryBacksOffAndSucceedsOnManualRetry() throws Exception {
        ((StubTransport) transport).fail = true;
        Merchant merchant = createMerchant("Failing Webhook Store");
        String apiKey = merchant.apiKey();
        UUID merchantId = merchant.id();
        createPayment(apiKey);

        MvcResult list = mockMvc.perform(get("/v1/webhooks/deliveries").header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode delivery = objectMapper.readTree(list.getResponse().getContentAsString()).get(0);
        UUID deliveryId = UUID.fromString(delivery.get("id").asText());
        assertEquals("FAILED", delivery.get("status").asText());
        assertEquals(1, delivery.get("attempts").asInt());
        assertNotNull(delivery.get("nextAttemptAt"),
                "A failed delivery must carry the next retry time (backoff)");

        // Recover the transport and retry manually: it delivers.
        ((StubTransport) transport).fail = false;
        mockMvc.perform(post("/v1/webhooks/deliveries/" + deliveryId + "/retry")
                        .header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.attempts").value(1));

        // The scheduled path also works once the backoff time has passed.
        ((StubTransport) transport).fail = true;
        createPayment(apiKey);
        Thread.sleep(50); // test backoff base is 10ms, so the retry is due
        ((StubTransport) transport).fail = false;
        dispatcher.retryDueDeliveries();
        assertEquals(0, deliveryService.listForMerchant(merchantId).stream()
                .filter(d -> d.getStatus() == WebhookDelivery.Status.FAILED).count(),
                "All failed deliveries should have been retried and sent");
    }

    private UUID createPayment(String apiKey) throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/payments")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CARD","amountMinor":5000,"currency":"USD","paymentDetails":"%s"}
                                """.formatted(TEST_PAN)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
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
