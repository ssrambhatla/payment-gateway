package com.gateway.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One idempotency-key record per merchant. The request fingerprint is a
 * SHA-256 hash of the canonical request — the raw sensitive value is never
 * stored, and the hash itself reveals nothing about the payload.
 */
@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"merchantId", "idempotencyKey"}))
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String idempotencyKey;

    /** SHA-256 hex of the canonical request (method|amount|currency|hash(details)). */
    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID id, UUID merchantId, String idempotencyKey,
                             String requestHash, UUID paymentId, Instant createdAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.paymentId = paymentId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
