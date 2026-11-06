package com.gateway.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.gateway.core.PaymentMethod;
import com.gateway.core.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A payment. This entity deliberately has NO field for raw card/bank values:
 * only the vault token and last4 are stored. The raw value exists only in
 * the token vault (in-memory, M1) and inside the PSP adapter call.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    /** Amount in minor units (cents). */
    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column
    private String pspReference;

    /** Opaque vault token. Never the raw card/bank value. */
    @Column(nullable = false)
    private String paymentToken;

    /** Last 4 digits/characters, for display only. */
    @Column(nullable = false, length = 4)
    private String last4;

    /** ISO country code of the payment (routing input, default "US"). */
    @Column(length = 2)
    private String country;

    /**
     * Settlement currency (what the merchant receives). Presentment currency
     * is {@link #currency}; the FX rate is locked at authorize time (M2).
     */
    @Column(length = 3)
    private String settlementCurrency;

    /** Locked FX rate: settlement-currency units per 1 presentment unit. */
    @Column(precision = 19, scale = 6)
    private BigDecimal fxRate;

    /** Authorized amount converted to the settlement currency (minor units). */
    @Column
    private Long settlementAmountMinor;

    /** When an ACH capture becomes due for settlement (batch delay model). */
    @Column
    private Instant settlementDueAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(UUID id, UUID merchantId, PaymentMethod method, long amountMinor,
                   String currency, PaymentStatus status, String paymentToken, String last4,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.method = method;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.paymentToken = paymentToken;
        this.last4 = last4;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public PaymentMethod getMethod() {
        return method;
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

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getPspReference() {
        return pspReference;
    }

    public void setPspReference(String pspReference) {
        this.pspReference = pspReference;
    }

    public String getPaymentToken() {
        return paymentToken;
    }

    public String getLast4() {
        return last4;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getSettlementCurrency() {
        return settlementCurrency;
    }

    public void setSettlementCurrency(String settlementCurrency) {
        this.settlementCurrency = settlementCurrency;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public void setFxRate(BigDecimal fxRate) {
        this.fxRate = fxRate;
    }

    public Long getSettlementAmountMinor() {
        return settlementAmountMinor;
    }

    public void setSettlementAmountMinor(Long settlementAmountMinor) {
        this.settlementAmountMinor = settlementAmountMinor;
    }

    public Instant getSettlementDueAt() {
        return settlementDueAt;
    }

    public void setSettlementDueAt(Instant settlementDueAt) {
        this.settlementDueAt = settlementDueAt;
    }
}
