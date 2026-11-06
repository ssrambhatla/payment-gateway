package com.gateway.core;

/**
 * One adapter per payment method (CARD, ACH, IBP). Adapters translate the
 * gateway's commands into calls against a PSP adapter. Adding a new payment
 * method means adding one class implementing this interface plus a registry
 * entry — core code does not change.
 */
public interface PaymentMethodAdapter {

    PaymentMethod method();

    AdapterResult authorize(AuthorizeCommand command);

    AdapterResult capture(CaptureCommand command);

    AdapterResult refund(RefundCommand command);
}
