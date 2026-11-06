package com.gateway.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Mock FX rate provider (M2): fixed rates per currency pair, quoted as
 * "units per 1 USD". The rate is locked at authorize time and stored on the
 * payment, so later rate moves never change an in-flight payment.
 *
 * <p>Rates are practice fixtures, not market data: USD 1.00, EUR 0.92,
 * GBP 0.79, INR 83.50, JPY 149.00, CAD 1.36.
 */
@Component
public class MockFxRateProvider implements FxService {

    private static final Map<String, BigDecimal> PER_USD = Map.of(
            "USD", BigDecimal.ONE,
            "EUR", new BigDecimal("0.92"),
            "GBP", new BigDecimal("0.79"),
            "INR", new BigDecimal("83.50"),
            "JPY", new BigDecimal("149.00"),
            "CAD", new BigDecimal("1.36"));

    @Override
    public BigDecimal rate(String fromCurrency, String toCurrency) {
        BigDecimal fromPerUsd = PER_USD.get(fromCurrency);
        BigDecimal toPerUsd = PER_USD.get(toCurrency);
        if (fromPerUsd == null || toPerUsd == null) {
            throw new IllegalArgumentException(
                    "Unsupported currency pair: " + fromCurrency + "->" + toCurrency);
        }
        return toPerUsd.divide(fromPerUsd, 6, RoundingMode.HALF_UP);
    }

    @Override
    public long convert(long amountMinor, String fromCurrency, String toCurrency) {
        return rate(fromCurrency, toCurrency)
                .multiply(BigDecimal.valueOf(amountMinor))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
