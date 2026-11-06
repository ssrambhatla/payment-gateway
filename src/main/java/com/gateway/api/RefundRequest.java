package com.gateway.api;

import jakarta.validation.constraints.Positive;

/** Refund request. {@code amountMinor} omitted (null) means a full refund. */
public record RefundRequest(
        @Positive(message = "amountMinor must be positive") Long amountMinor) {
}
