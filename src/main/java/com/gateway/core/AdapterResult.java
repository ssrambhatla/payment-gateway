package com.gateway.core;

/** Result of an adapter operation. Carries the PSP reference on success. */
public record AdapterResult(
        boolean success,
        String pspReference,
        String failureReason) {

    public static AdapterResult ok(String pspReference) {
        return new AdapterResult(true, pspReference, null);
    }

    public static AdapterResult failed(String failureReason) {
        return new AdapterResult(false, null, failureReason);
    }
}
