package com.gateway.api;

import java.time.Instant;
import java.util.UUID;

import com.gateway.domain.WebhookDelivery;

/** Webhook delivery record. The payload is shown (it carries no PAN/token). */
public record WebhookDeliveryResponse(
        UUID id,
        UUID paymentId,
        String event,
        String status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        String signature,
        String payload,
        Instant createdAt,
        Instant deliveredAt) {

    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getPaymentId(),
                delivery.getEvent(),
                delivery.getStatus().name(),
                delivery.getAttempts(),
                delivery.getNextAttemptAt(),
                delivery.getLastError(),
                delivery.getSignature(),
                delivery.getPayload(),
                delivery.getCreatedAt(),
                delivery.getDeliveredAt());
    }
}
