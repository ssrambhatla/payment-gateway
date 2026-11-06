package com.gateway.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The payment state machine. Every status change in the system must go
 * through {@link #validateTransition(PaymentStatus, PaymentStatus)}.
 *
 * <pre>
 * PENDING -&gt; AUTHORIZED, FAILED
 * AUTHORIZED -&gt; CAPTURED, VOIDED, FAILED
 * CAPTURED -&gt; SETTLED, PARTIALLY_REFUNDED, REFUNDED
 * PARTIALLY_REFUNDED -&gt; REFUNDED, PARTIALLY_REFUNDED
 * SETTLED, FAILED, REFUNDED, VOIDED: terminal, no outgoing transitions
 * </pre>
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED));
        ALLOWED.put(PaymentStatus.AUTHORIZED,
                EnumSet.of(PaymentStatus.CAPTURED, PaymentStatus.VOIDED, PaymentStatus.FAILED));
        ALLOWED.put(PaymentStatus.CAPTURED,
                EnumSet.of(PaymentStatus.SETTLED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        ALLOWED.put(PaymentStatus.PARTIALLY_REFUNDED,
                EnumSet.of(PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED));
        ALLOWED.put(PaymentStatus.SETTLED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(PaymentStatus.VOIDED, EnumSet.noneOf(PaymentStatus.class));
    }

    private PaymentStateMachine() {
    }

    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Throws {@link InvalidTransitionException} when the transition is not allowed. */
    public static void validateTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }
}
