package com.gateway.webhook;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.core.GatewayProperties;
import com.gateway.core.WebhookNotifier;
import com.gateway.domain.Payment;
import com.gateway.domain.WebhookDelivery;
import com.gateway.domain.WebhookDeliveryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signed webhook dispatcher (M2). Replaces the M1 logging notifier as the
 * {@link WebhookNotifier} implementation; the logging behavior survives as
 * the {@link WebhookTransport}.
 *
 * <p>Flow: build the event payload → sign it (HMAC-SHA256 over
 * {@code timestamp + "." + json}, Stripe-style {@code t=<ts>,v1=<hex>}) →
 * record a {@link WebhookDelivery} → attempt delivery through the transport.
 * A failed attempt is recorded FAILED with the next retry time from
 * exponential backoff (base × 2^(attempt-1)); a scheduled job retries due
 * deliveries, and merchants can retry manually via the test endpoint.
 * After {@code max-attempts} failures the delivery is dead-lettered
 * (nextAttemptAt null) until a manual retry.
 */
@Component
public class WebhookDispatcher implements WebhookNotifier {

    private final WebhookDeliveryRepository deliveries;
    private final WebhookTransport transport;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final HmacSigner signer;

    public WebhookDispatcher(WebhookDeliveryRepository deliveries, WebhookTransport transport,
                             GatewayProperties properties, ObjectMapper objectMapper,
                             HmacSigner signer) {
        this.deliveries = deliveries;
        this.transport = transport;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.signer = signer;
    }

    @Override
    @Transactional
    public void notify(Payment payment, String event) {
        WebhookDelivery delivery = buildDelivery(payment, event);
        deliveries.save(delivery);
        attemptDelivery(delivery);
    }

    /** Builds + signs a delivery for a payment event (payload has no PAN/token). */
    WebhookDelivery buildDelivery(Payment payment, String event) {
        long timestamp = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("merchantId", payment.getMerchantId().toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("method", payment.getMethod().name());
        payload.put("status", payment.getStatus().name());
        payload.put("amountMinor", payment.getAmountMinor());
        payload.put("currency", payment.getCurrency());
        payload.put("settlementCurrency", payment.getSettlementCurrency());
        payload.put("timestamp", timestamp);
        String json = toJson(payload);
        String signature = "t=" + timestamp + ",v1=" + signer.sign(
                properties.webhookSecret(), timestamp + "." + json);
        return new WebhookDelivery(UUID.randomUUID(), payment.getMerchantId(), payment.getId(),
                event, json, signature, WebhookDelivery.Status.PENDING, 0, null, null,
                Instant.now(), null);
    }

    /** One delivery attempt: counts the attempt, marks SENT or schedules a retry. */
    @Transactional
    public void attemptDelivery(WebhookDelivery delivery) {
        delivery.setAttempts(delivery.getAttempts() + 1);
        boolean ok = false;
        try {
            ok = transport.deliver(delivery);
        } catch (RuntimeException e) {
            delivery.setLastError(e.getMessage());
        }
        if (ok) {
            delivery.setStatus(WebhookDelivery.Status.SENT);
            delivery.setDeliveredAt(Instant.now());
            delivery.setNextAttemptAt(null);
            delivery.setLastError(null);
        } else {
            delivery.setStatus(WebhookDelivery.Status.FAILED);
            if (delivery.getLastError() == null) {
                delivery.setLastError("transport returned false");
            }
            int maxAttempts = properties.webhook().maxAttempts();
            if (delivery.getAttempts() < maxAttempts) {
                long backoffMs = properties.webhook().backoffBaseMs()
                        * (1L << (delivery.getAttempts() - 1));
                delivery.setNextAttemptAt(Instant.now().plusMillis(backoffMs));
            } else {
                // Dead-lettered until a manual retry.
                delivery.setNextAttemptAt(null);
            }
        }
        deliveries.save(delivery);
    }

    /** Scheduled retry job: picks up FAILED deliveries whose backoff has elapsed. */
    @Scheduled(fixedDelayString = "${gateway.webhook-retry-delay-ms:30000}")
    @Transactional
    public void retryDueDeliveries() {
        for (WebhookDelivery delivery
                : deliveries.findByStatusAndNextAttemptAtBefore(
                        WebhookDelivery.Status.FAILED, Instant.now())) {
            attemptDelivery(delivery);
        }
    }

    /** Manual retry (merchant test endpoint): resets the attempt count and tries again. */
    @Transactional
    public WebhookDelivery retry(UUID merchantId, UUID deliveryId) {
        WebhookDelivery delivery = deliveries.findById(deliveryId)
                .filter(d -> d.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new IllegalArgumentException("Webhook delivery not found: " + deliveryId));
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(null);
        delivery.setLastError(null);
        attemptDelivery(delivery);
        return delivery;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }
}
