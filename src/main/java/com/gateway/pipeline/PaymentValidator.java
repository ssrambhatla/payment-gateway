package com.gateway.pipeline;

import com.gateway.core.PaymentMethod;
import org.springframework.stereotype.Component;

/**
 * Validation stage of the payment pipeline. Runs before tokenization, so an
 * invalid request never creates a payment, a token, or a ledger entry.
 * (Bean Validation on the request DTO is the first line; this is the
 * service-level stage, so the pipeline stays correct for non-HTTP callers.)
 */
@Component
public class PaymentValidator {

    public void validate(PaymentMethod method, long amountMinor, String currency, String country) {
        if (method == null) {
            throw new IllegalArgumentException("method is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code");
        }
        if (country == null || !country.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("country must be a 2-letter ISO code");
        }
    }
}
