package com.gateway.settlement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.gateway.core.GatewayProperties;
import com.gateway.core.PaymentMethod;
import com.gateway.core.PaymentStateMachine;
import com.gateway.core.PaymentStatus;
import com.gateway.core.WebhookNotifier;
import com.gateway.domain.Payment;
import com.gateway.domain.PaymentRepository;
import com.gateway.ledger.LedgerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settlement (M2, mock batch model).
 *
 * <ul>
 *   <li><b>ACH</b> — a captured payment does not settle at once. Capture
 *       stamps {@code settlementDueAt = now + gateway.ach.settlement-delay-ms}
 *       (2 days by default); a scheduled batch job settles due captures,
 *       moving CAPTURED → SETTLED with a SETTLE ledger posting.</li>
 *   <li><b>IBP</b> — instant: capture settles in the same call, no delay.</li>
 *   <li><b>CARD</b> — settlement is M4; this service leaves card captures
 *       in CAPTURED.</li>
 * </ul>
 *
 * <p>The batch job is also callable directly ({@link #settleDuePayments()}),
 * which is how tests and the failover/settlement drill trigger it.
 */
@Service
public class SettlementService {

    private final PaymentRepository payments;
    private final LedgerService ledger;
    private final WebhookNotifier notifier;
    private final GatewayProperties properties;

    public SettlementService(PaymentRepository payments, LedgerService ledger,
                             WebhookNotifier notifier, GatewayProperties properties) {
        this.payments = payments;
        this.ledger = ledger;
        this.notifier = notifier;
        this.properties = properties;
    }

    /**
     * Called after a capture. ACH gets a settlement due date; IBP settles
     * immediately; CARD is left for M4 settlement.
     */
    @Transactional
    public void scheduleSettlement(Payment payment) {
        if (payment.getMethod() == PaymentMethod.ACH) {
            payment.setSettlementDueAt(
                    Instant.now().plusMillis(properties.ach().settlementDelayMs()));
            payments.save(payment);
        } else if (payment.getMethod() == PaymentMethod.IBP) {
            settle(payment);
        }
    }

    /** Batch job: settles every captured payment whose due date has passed. */
    @Scheduled(fixedDelayString = "${gateway.settlement-job-delay-ms:30000}")
    @Transactional
    public void settleDuePayments() {
        List<Payment> due = payments.findByStatusAndSettlementDueAtBefore(
                PaymentStatus.CAPTURED, Instant.now());
        for (Payment payment : due) {
            settle(payment);
        }
    }

    /** Settles one captured payment: SETTLE posting + CAPTURED → SETTLED. */
    @Transactional
    public void settle(Payment payment) {
        PaymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.SETTLED);
        ledger.postSettle(payment);
        payment.setStatus(PaymentStatus.SETTLED);
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);
        notifier.notify(payment, "payment.settled");
    }

    /** Test/drill helper: settle one payment by id. */
    @Transactional
    public void settleById(UUID paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        settle(payment);
    }
}
