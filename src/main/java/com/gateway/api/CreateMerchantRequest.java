package com.gateway.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/** Merchant onboarding request. */
public record CreateMerchantRequest(
        @NotBlank String name) {
}
