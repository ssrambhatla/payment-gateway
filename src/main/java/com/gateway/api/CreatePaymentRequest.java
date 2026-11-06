package com.gateway.api;

import com.gateway.core.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Payment creation request.
 *
 * <p>{@code paymentDetails} is the TEST-ONLY sensitive value: a test card
 * number for CARD, a mock bank account id for ACH, a mock bank reference
 * for IBP. It is tokenized immediately and never stored.
 */
public record CreatePaymentRequest(
        @NotNull PaymentMethod method,
        @Positive long amountMinor,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code") String currency,
        @NotBlank String paymentDetails,
        @Pattern(regexp = "^[A-Z]{2}$", message = "country must be a 2-letter ISO code") String country) {
}
