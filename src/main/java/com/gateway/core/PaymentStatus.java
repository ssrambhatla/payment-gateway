package com.gateway.core;

/**
 * Payment lifecycle. Terminal states (no outgoing transitions):
 * SETTLED, FAILED, REFUNDED, VOIDED.
 */
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    SETTLED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    VOIDED;

    public boolean isTerminal() {
        return this == SETTLED || this == FAILED || this == REFUNDED || this == VOIDED;
    }
}
