package com.gateway.core;

import java.util.UUID;

/** Command passed to a payment-method adapter for capture. */
public record CaptureCommand(
        String merchantId,
        String pspReference,
        long amountMinor,
        String currency,
        String country,
        PaymentMethod method,
        UUID paymentId) {
}
