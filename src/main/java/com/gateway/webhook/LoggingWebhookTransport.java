package com.gateway.webhook;

import com.gateway.domain.WebhookDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * M2 transport: logs the signed webhook payload (payment id + status only in
 * the log line; the full payload is logged at DEBUG). Never logs tokens,
 * card/bank values, or API keys. Always reports success — real delivery
 * failures are simulated in tests with a stub transport.
 */
@Component
public class LoggingWebhookTransport implements WebhookTransport {

    private static final Logger log = LoggerFactory.getLogger(LoggingWebhookTransport.class);

    @Override
    public boolean deliver(WebhookDelivery delivery) {
        String signature = delivery.getSignature();
        String shown = signature.length() > 16 ? signature.substring(0, 16) + "..." : signature;
        log.info("webhook deliver event={} paymentId={} signature={}",
                delivery.getEvent(), delivery.getPaymentId(), shown);
        log.debug("webhook payload={}", delivery.getPayload());
        return true;
    }
}
