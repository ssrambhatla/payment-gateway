package com.gateway.core;

/** Payment methods accepted by the gateway. Each has one PaymentMethodAdapter. */
public enum PaymentMethod {
    CARD,
    ACH,
    IBP
}
