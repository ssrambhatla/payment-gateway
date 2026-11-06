package com.gateway.core;

/**
 * One adapter per payment service provider. The adapter receives a payment
 * TOKEN, never raw card/bank details — detokenization happens only inside
 * the adapter (see the token vault). M1 ships a single mock PSP; M2 adds a
 * second PSP plus failover.
 */
public interface PSPAdapter {

    /** Unique name used in config, e.g. "mock-psp". */
    String name();

    AdapterResult authorize(String paymentToken, long amountMinor, String currency);

    AdapterResult capture(String pspReference, long amountMinor, String currency);

    AdapterResult refund(String pspReference, long amountMinor, String currency);
}
