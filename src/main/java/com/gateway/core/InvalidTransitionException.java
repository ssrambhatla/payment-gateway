package com.gateway.core;

/** Thrown when a payment state transition is not allowed. Maps to HTTP 409. */
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Invalid payment transition: " + from + " -> " + to);
    }
}
