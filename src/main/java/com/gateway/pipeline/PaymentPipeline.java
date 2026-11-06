package com.gateway.pipeline;

import com.gateway.core.AdapterRegistry;
import com.gateway.core.AdapterResult;
import com.gateway.core.AuthorizeCommand;
import com.gateway.core.CaptureCommand;
import com.gateway.core.FraudCheck;
import com.gateway.core.PaymentMethod;
import com.gateway.core.RefundCommand;
import com.gateway.domain.Payment;
import org.springframework.stereotype.Component;

/**
 * The payment pipeline (M2): the ordered hook stages every payment flows
 * through.
 *
 * <pre>
 * validate → tokenize (in PaymentService) → fraud hook → routing stage
 * → ledger → notify (in PaymentService)
 * </pre>
 *
 * <p>Each stage is an extension point: {@link PaymentValidator} for
 * validation, {@link FraudCheck} for fraud, the method adapter +
 * {@code PspRouter} for routing. A new stage implements its hook interface
 * and is wired here; the service and the adapters do not change.
 */
@Component
public class PaymentPipeline {

    private final PaymentValidator validator;
    private final FraudCheck fraudCheck;
    private final AdapterRegistry registry;

    public PaymentPipeline(PaymentValidator validator, FraudCheck fraudCheck,
                           AdapterRegistry registry) {
        this.validator = validator;
        this.fraudCheck = fraudCheck;
        this.registry = registry;
    }

    /** Validation stage. */
    public void validate(PaymentMethod method, long amountMinor, String currency, String country) {
        validator.validate(method, amountMinor, currency, country);
    }

    /**
     * Fraud stage + routing stage for authorization. Returns a failed result
     * when the fraud hook rejects the payment (the service maps that to
     * FAILED, same as a PSP decline).
     */
    public AdapterResult authorize(Payment payment, String token) {
        if (!fraudCheck.approve(payment)) {
            return AdapterResult.failed("fraud_rejected");
        }
        return registry.methodAdapterFor(payment.getMethod()).authorize(
                new AuthorizeCommand(payment.getMerchantId().toString(), token,
                        payment.getAmountMinor(), payment.getCurrency(),
                        payment.getCountry(), payment.getMethod(), payment.getId()));
    }

    /** Routing stage for capture. */
    public AdapterResult capture(Payment payment) {
        return registry.methodAdapterFor(payment.getMethod()).capture(
                new CaptureCommand(payment.getMerchantId().toString(), payment.getPspReference(),
                        payment.getAmountMinor(), payment.getCurrency(),
                        payment.getCountry(), payment.getMethod(), payment.getId()));
    }

    /** Routing stage for refunds. */
    public AdapterResult refund(Payment payment, long refundAmountMinor) {
        return registry.methodAdapterFor(payment.getMethod()).refund(
                new RefundCommand(payment.getMerchantId().toString(), payment.getPspReference(),
                        refundAmountMinor, payment.getCurrency(),
                        payment.getCountry(), payment.getMethod(), payment.getId()));
    }
}
