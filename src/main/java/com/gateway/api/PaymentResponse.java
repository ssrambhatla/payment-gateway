package com.gateway.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.gateway.core.PaymentMethod;
import com.gateway.core.PaymentStatus;
import com.gateway.domain.Payment;

/** Payment response. Contains no token and no sensitive value. */
public record PaymentResponse(
        UUID id,
        PaymentMethod method,
        long amountMinor,
        String currency,
        PaymentStatus status,
        String pspReference,
        String last4,
        Instant createdAt,
        String country,
        String settlementCurrency,
        BigDecimal fxRate,
        Long settlementAmountMinor,
        Instant settlementDueAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMethod(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPspReference(),
                payment.getLast4(),
                payment.getCreatedAt(),
                payment.getCountry(),
                payment.getSettlementCurrency(),
                payment.getFxRate(),
                payment.getSettlementAmountMinor(),
                payment.getSettlementDueAt());
    }
}
