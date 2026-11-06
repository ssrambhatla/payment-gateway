package com.gateway.core;

import java.util.UUID;

/** Command passed to a payment-method adapter for authorization. */
public record AuthorizeCommand(
        String merchantId,
        String paymentToken,
        long amountMinor,
        String currency,
        String country,
        PaymentMethod method,
        UUID paymentId) {
}
