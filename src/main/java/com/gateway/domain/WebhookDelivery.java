package com.gateway.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * One outbound webhook delivery attempt record. The dispatcher signs the
 * payload, hands it to the transport, and tracks retries with exponential
 * backoff here. Payloads carry ids/amounts/status only — never a PAN, token,
 * or API key.
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    public enum Status {
        PENDING, SENT, FAILED
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private String event;

    /** Signed JSON payload (Stripe-style: timestamp + "." + JSON, HMAC-SHA256). */
    @Lob
    @Column(nullable = false)
    private String payload;

    /** Signature header value: {@code t=<epoch>,v1=<hex>}. */
    @Column(nullable = false)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** Attempts made so far (1 = first try). */
    @Column(nullable = false)
    private int attempts;

    /** Next retry time (exponential backoff); null when sent or dead-lettered. */
    @Column
    private Instant nextAttemptAt;

    @Column
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant deliveredAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(UUID id, UUID merchantId, UUID paymentId, String event,
                           String payload, String signature, Status status, int attempts,
                           Instant nextAttemptAt, String lastError, Instant createdAt,
                           Instant deliveredAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.paymentId = paymentId;
        this.event = event;
        this.payload = payload;
        this.signature = signature;
        this.status = status;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getEvent() {
        return event;
    }

    public String getPayload() {
        return payload;
    }

    public String getSignature() {
        return signature;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
