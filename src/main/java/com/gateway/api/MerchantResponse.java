package com.gateway.api;

import java.util.UUID;

/**
 * Merchant response. The API key is returned ONLY here, at creation time —
 * it is never returned again and never logged.
 */
public record MerchantResponse(
        UUID merchantId,
        String apiKey) {
}
