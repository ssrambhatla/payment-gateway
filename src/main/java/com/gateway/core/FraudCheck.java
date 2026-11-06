package com.gateway.core;

import com.gateway.domain.Payment;

/**
 * Fraud-check hook in the payment pipeline. Runs after validation and
 * tokenization, before routing. M1 ships an allow-all implementation;
 * real checks plug in here without touching the pipeline.
 */
public interface FraudCheck {

    /** Returns false when the payment must be rejected. */
    boolean approve(Payment payment);
}
