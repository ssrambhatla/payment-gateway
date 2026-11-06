package com.gateway.fx;

import java.math.BigDecimal;

/**
 * FX rate service. The mock implementation returns fixed rates; a real
 * provider would plug in here without touching the ledger or the pipeline.
 */
public interface FxService {

    /**
     * Locked rate: units of {@code toCurrency} per 1 unit of
     * {@code fromCurrency}. Throws {@link IllegalArgumentException} for an
     * unsupported currency pair.
     */
    BigDecimal rate(String fromCurrency, String toCurrency);

    /** Converts a minor-unit amount, rounded half-up to a whole minor unit. */
    long convert(long amountMinor, String fromCurrency, String toCurrency);
}
