package com.gateway.api;

/** Error body for API failures. */
public record ErrorResponse(
        String error,
        String message) {
}
