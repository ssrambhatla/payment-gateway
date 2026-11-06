package com.gateway.domain;

import java.time.Instant;
import java.util.UUID;

import com.gateway.core.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One refund of a captured payment (full or partial). */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    /** REFUNDED (full) or PARTIALLY_REFUNDED — mirrors the payment status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column
    private String pspReference;

    @Column(nullable = false)
    private Instant createdAt;

    protected Refund() {
    }

    public Refund(UUID id, UUID paymentId, UUID merchantId, long amountMinor,
                  String currency, PaymentStatus status, String pspReference, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.pspReference = pspReference;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPspReference() {
        return pspReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
