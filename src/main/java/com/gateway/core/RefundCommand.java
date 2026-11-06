package com.gateway.core;

import java.util.UUID;

/** Command passed to a payment-method adapter for a refund (used from M2). */
public record RefundCommand(
        String merchantId,
        String pspReference,
        long amountMinor,
        String currency,
        String country,
        PaymentMethod method,
        UUID paymentId) {
}
