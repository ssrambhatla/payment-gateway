package com.gateway.api;

import java.util.UUID;

import com.gateway.domain.Payment;
import com.gateway.domain.Refund;
import com.gateway.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment endpoints. All require the X-Api-Key header (see ApiKeyAuthFilter);
 * the authenticated merchant id is in the request attribute "merchantId".
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /**
     * Creates a payment and authorizes it through the mock PSP (happy path).
     * With an {@code Idempotency-Key} header, a retry with the same key and
     * payload replays the original payment (HTTP 200); the same key with a
     * different payload is a 409.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request,
                                                  @RequestHeader(value = "Idempotency-Key", required = false)
                                                  String idempotencyKey,
                                                  HttpServletRequest http) {
        UUID merchantId = merchantId(http);
        PaymentService.CreatePaymentResult result = payments.createPayment(
                merchantId, request.method(), request.amountMinor(), request.currency(),
                request.country(), request.paymentDetails(), idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(PaymentResponse.from(result.payment()));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id, HttpServletRequest http) {
        return PaymentResponse.from(payments.getPayment(merchantId(http), id));
    }

    /** Full-amount capture. Only valid from AUTHORIZED. */
    @PostMapping("/{id}/capture")
    public PaymentResponse capture(@PathVariable UUID id, HttpServletRequest http) {
        return PaymentResponse.from(payments.capturePayment(merchantId(http), id));
    }

    /**
     * Refund of a captured payment. Body {@code {"amountMinor": 2500}} refunds
     * that amount; an empty body refunds the full remaining balance. Partial
     * refunds move the payment to PARTIALLY_REFUNDED, a full refund to
     * REFUNDED. Refund before capture is a 409.
     */
    @PostMapping("/{id}/refund")
    public RefundResponse refund(@PathVariable UUID id,
                                 @Valid @RequestBody(required = false) RefundRequest request,
                                 HttpServletRequest http) {
        Long amountMinor = request == null ? null : request.amountMinor();
        Refund refund = payments.refundPayment(merchantId(http), id, amountMinor);
        return RefundResponse.from(refund);
    }

    private UUID merchantId(HttpServletRequest http) {
        return (UUID) http.getAttribute(ApiKeyAuthFilter.MERCHANT_ID_ATTRIBUTE);
    }
}
