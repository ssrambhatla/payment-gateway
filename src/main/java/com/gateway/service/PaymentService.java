package com.gateway.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.gateway.core.AdapterResult;
import com.gateway.core.GatewayProperties;
import com.gateway.core.IdempotencyConflictException;
import com.gateway.core.PaymentMethod;
import com.gateway.core.PaymentStateMachine;
import com.gateway.core.PaymentStatus;
import com.gateway.core.WebhookNotifier;
import com.gateway.domain.Payment;
import com.gateway.domain.PaymentRepository;
import com.gateway.domain.Refund;
import com.gateway.domain.RefundRepository;
import com.gateway.fx.FxService;
import com.gateway.idempotency.IdempotencyService;
import com.gateway.ledger.LedgerService;
import com.gateway.pipeline.PaymentPipeline;
import com.gateway.settlement.SettlementService;
import com.gateway.tokenization.TokenVault;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment service: the M2 pipeline.
 *
 * <pre>
 * create:  idempotency replay? → validate → tokenize → FX lock → save PENDING
 *          → pipeline (fraud → routing) → ledger → notify → record idempotency
 * capture: state guard → pipeline (routing) → ledger → notify → settlement
 * refund:  state guard → amount guard → pipeline (routing) → ledger → notify
 * </pre>
 *
 * <p>The raw sensitive value exists only as a method argument: it is hashed
 * for the idempotency fingerprint, tokenized, and never stored.
 */
@Service
public class PaymentService {

    /** Result of a creation: the payment, plus whether it was an idempotent replay. */
    public record CreatePaymentResult(Payment payment, boolean replayed) {
    }

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final TokenVault vault;
    private final LedgerService ledger;
    private final WebhookNotifier notifier;
    private final PaymentPipeline pipeline;
    private final IdempotencyService idempotency;
    private final FxService fx;
    private final SettlementService settlement;
    private final GatewayProperties properties;

    public PaymentService(PaymentRepository payments, RefundRepository refunds, TokenVault vault,
                          LedgerService ledger, WebhookNotifier notifier, PaymentPipeline pipeline,
                          IdempotencyService idempotency, FxService fx,
                          SettlementService settlement, GatewayProperties properties) {
        this.payments = payments;
        this.refunds = refunds;
        this.vault = vault;
        this.ledger = ledger;
        this.notifier = notifier;
        this.pipeline = pipeline;
        this.idempotency = idempotency;
        this.fx = fx;
        this.settlement = settlement;
        this.properties = properties;
    }

    /**
     * Creates a payment in PENDING, tokenizes the sensitive value immediately,
     * locks the FX rate, then authorizes through the pipeline. The raw value
     * never reaches the database, the ledger, or any log.
     *
     * @param country        ISO country code (null/blank → "US"); routing input
     * @param idempotencyKey optional {@code Idempotency-Key} header value
     */
    @Transactional
    public CreatePaymentResult createPayment(UUID merchantId, PaymentMethod method,
                                             long amountMinor, String currency, String country,
                                             String sensitiveValue, String idempotencyKey) {
        String effectiveCountry = country == null || country.isBlank() ? "US" : country;

        // Idempotency: a replay returns the original payment, no second charge.
        String fingerprintToRecord = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            final String fingerprint = idempotency.fingerprint(method, amountMinor, currency, sensitiveValue);
            Optional<UUID> originalId = idempotency.find(merchantId, idempotencyKey)
                    .map(record -> {
                        if (!record.getRequestHash().equals(fingerprint)) {
                            throw new IdempotencyConflictException(idempotencyKey);
                        }
                        return record.getPaymentId();
                    });
            if (originalId.isPresent()) {
                Payment original = payments.findById(originalId.get())
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record points at a missing payment: " + originalId.get()));
                return new CreatePaymentResult(original, true);
            }
            fingerprintToRecord = fingerprint;
        }

        pipeline.validate(method, amountMinor, currency, effectiveCountry);
        String token = vault.tokenize(merchantId.toString(), sensitiveValue);
        String last4 = vault.last4(sensitiveValue);

        Payment payment = new Payment(UUID.randomUUID(), merchantId, method, amountMinor,
                currency, PaymentStatus.PENDING, token, last4, Instant.now(), Instant.now());
        payment.setCountry(effectiveCountry);
        // FX rate locked at authorize time (M2).
        String settlementCurrency = properties.settlementCurrency();
        payment.setSettlementCurrency(settlementCurrency);
        payment.setFxRate(fx.rate(currency, settlementCurrency));
        payment.setSettlementAmountMinor(fx.convert(amountMinor, currency, settlementCurrency));
        payment = payments.save(payment);

        AdapterResult result = pipeline.authorize(payment, token);
        if (result.success()) {
            payment.setPspReference(result.pspReference());
            transition(payment, PaymentStatus.AUTHORIZED);
            ledger.postAuthorization(payment);
            notifier.notify(payment, "payment.authorized");
        } else {
            transition(payment, PaymentStatus.FAILED);
        }

        if (fingerprintToRecord != null) {
            idempotency.record(merchantId, idempotencyKey, fingerprintToRecord, payment.getId());
        }
        return new CreatePaymentResult(payment, false);
    }

    /** Full-amount capture. Only valid from AUTHORIZED (guard enforced). */
    @Transactional
    public Payment capturePayment(UUID merchantId, UUID paymentId) {
        Payment payment = getOwnedPayment(merchantId, paymentId);
        PaymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.CAPTURED);

        AdapterResult result = pipeline.capture(payment);
        if (!result.success()) {
            throw new IllegalStateException("PSP capture failed: " + result.failureReason());
        }
        transition(payment, PaymentStatus.CAPTURED);
        ledger.postCapture(payment);
        notifier.notify(payment, "payment.captured");
        settlement.scheduleSettlement(payment);
        return payment;
    }

    /**
     * Refund of a captured payment. {@code amountMinor} null means a full
     * refund of the remaining refundable balance. Partial refunds move the
     * payment to PARTIALLY_REFUNDED; refunding the last of the balance moves
     * it to REFUNDED. Refund before capture is a 409 (state machine guard).
     */
    @Transactional
    public Refund refundPayment(UUID merchantId, UUID paymentId, Long amountMinor) {
        Payment payment = getOwnedPayment(merchantId, paymentId);
        long remaining = payment.getAmountMinor() - ledger.refundedAmountMinor(payment.getId());
        long refundAmount = amountMinor == null ? remaining : amountMinor;
        PaymentStatus target = refundAmount == remaining
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        // State guard first: a terminal payment (e.g. already REFUNDED) is a
        // 409, even if the amount would also be invalid.
        PaymentStateMachine.validateTransition(payment.getStatus(), target);
        if (refundAmount <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        if (refundAmount > remaining) {
            throw new IllegalArgumentException(
                    "Refund amount exceeds refundable balance: " + remaining);
        }

        AdapterResult result = pipeline.refund(payment, refundAmount);
        if (!result.success()) {
            throw new IllegalStateException("PSP refund failed: " + result.failureReason());
        }
        Refund refund = new Refund(UUID.randomUUID(), payment.getId(), merchantId,
                refundAmount, payment.getCurrency(), target, result.pspReference(), Instant.now());
        refunds.save(refund);
        transition(payment, target);
        ledger.postRefund(payment, refundAmount);
        notifier.notify(payment, "payment.refunded");
        return refund;
    }

    public Payment getPayment(UUID merchantId, UUID paymentId) {
        return getOwnedPayment(merchantId, paymentId);
    }

    private Payment getOwnedPayment(UUID merchantId, UUID paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (!payment.getMerchantId().equals(merchantId)) {
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }
        return payment;
    }

    private void transition(Payment payment, PaymentStatus to) {
        PaymentStateMachine.validateTransition(payment.getStatus(), to);
        payment.setStatus(to);
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);
    }
}
