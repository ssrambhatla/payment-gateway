package com.gateway;

import static com.gateway.core.PaymentStatus.AUTHORIZED;
import static com.gateway.core.PaymentStatus.CAPTURED;
import static com.gateway.core.PaymentStatus.FAILED;
import static com.gateway.core.PaymentStatus.PARTIALLY_REFUNDED;
import static com.gateway.core.PaymentStatus.PENDING;
import static com.gateway.core.PaymentStatus.REFUNDED;
import static com.gateway.core.PaymentStatus.SETTLED;
import static com.gateway.core.PaymentStatus.VOIDED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gateway.core.InvalidTransitionException;
import com.gateway.core.PaymentStateMachine;
import org.junit.jupiter.api.Test;

/** Unit tests for the payment state machine, including invalid transitions. */
class PaymentStateMachineTest {

    @Test
    void validTransitionsAreAllowed() {
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(PENDING, AUTHORIZED));
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(PENDING, FAILED));
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(AUTHORIZED, CAPTURED));
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(AUTHORIZED, VOIDED));
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(CAPTURED, SETTLED));
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(CAPTURED, PARTIALLY_REFUNDED));
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(CAPTURED, REFUNDED));
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(PARTIALLY_REFUNDED, REFUNDED));
        assertTrue(PaymentStateMachine.canTransition(PENDING, AUTHORIZED));
    }

    @Test
    void refundBeforeCaptureIsRejected() {
        // The classic invalid transition: refund requires a prior capture.
        assertThrows(InvalidTransitionException.class,
                () -> PaymentStateMachine.validateTransition(PENDING, REFUNDED));
        assertThrows(InvalidTransitionException.class,
                () -> PaymentStateMachine.validateTransition(AUTHORIZED, REFUNDED));
    }

    @Test
    void captureBeforeAuthorizationIsRejected() {
        assertThrows(InvalidTransitionException.class,
                () -> PaymentStateMachine.validateTransition(PENDING, CAPTURED));
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (var terminal : new com.gateway.core.PaymentStatus[]{SETTLED, FAILED, REFUNDED, VOIDED}) {
            for (var target : com.gateway.core.PaymentStatus.values()) {
                assertThrows(InvalidTransitionException.class,
                        () -> PaymentStateMachine.validateTransition(terminal, target),
                        terminal + " -> " + target + " must be rejected");
            }
        }
    }
}
