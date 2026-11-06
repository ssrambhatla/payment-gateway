package com.gateway.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One side of a double-entry ledger posting. Every posting for a payment has
 * a matching debit and credit of the same amount (see LedgerService), so
 * balances are always derived from entries, never stored separately.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public enum Direction {
        DEBIT, CREDIT
    }

    public enum EntryType {
        AUTH, CAPTURE, REFUND, SETTLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID paymentId;

    /** Account name, e.g. PSP_PENDING, PSP_CLEARING, MERCHANT_PAYABLE. */
    @Column(nullable = false)
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Settlement currency of the converted amount (M2). */
    @Column(length = 3)
    private String settlementCurrency;

    /** Amount converted to the settlement currency (minor units, M2). */
    @Column
    private Long settlementAmountMinor;

    /** FX rate applied: settlement units per 1 presentment unit (M2). */
    @Column(precision = 19, scale = 6)
    private BigDecimal fxRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType entryType;

    @Column(nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID paymentId, String account, Direction direction,
                       long amountMinor, String currency, EntryType entryType, Instant createdAt) {
        this(paymentId, account, direction, amountMinor, currency,
                currency, amountMinor, null, entryType, createdAt);
    }

    public LedgerEntry(UUID paymentId, String account, Direction direction,
                       long amountMinor, String currency, String settlementCurrency,
                       long settlementAmountMinor, BigDecimal fxRate,
                       EntryType entryType, Instant createdAt) {
        this.paymentId = paymentId;
        this.account = account;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.settlementCurrency = settlementCurrency;
        this.settlementAmountMinor = settlementAmountMinor;
        this.fxRate = fxRate;
        this.entryType = entryType;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getAccount() {
        return account;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSettlementCurrency() {
        return settlementCurrency;
    }

    public Long getSettlementAmountMinor() {
        return settlementAmountMinor;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
