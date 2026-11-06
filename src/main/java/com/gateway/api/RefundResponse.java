package com.gateway.api;

import java.time.Instant;
import java.util.UUID;

import com.gateway.core.PaymentStatus;
import com.gateway.domain.Refund;

/** Refund response. Contains no token and no sensitive value. */
public record RefundResponse(
        UUID id,
        UUID paymentId,
        long amountMinor,
        String currency,
        PaymentStatus status,
        String pspReference,
        Instant createdAt) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getAmountMinor(),
                refund.getCurrency(),
                refund.getStatus(),
                refund.getPspReference(),
                refund.getCreatedAt());
    }
}
