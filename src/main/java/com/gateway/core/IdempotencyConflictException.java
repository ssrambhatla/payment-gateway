package com.gateway.core;

/** Same Idempotency-Key reused with a different request payload. Maps to HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request");
    }
}
