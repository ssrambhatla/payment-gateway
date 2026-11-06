package com.gateway.webhook;

import com.gateway.domain.WebhookDelivery;

/**
 * Outbound webhook transport — the last mile to the merchant. M2 ships a
 * logging transport (the M1 notifier's behavior); a real HTTP transport plugs
 * in here later (M3, over SQS) without touching the dispatcher.
 */
public interface WebhookTransport {

    /**
     * Attempts one delivery. Returns true on success; false (or an exception)
     * when the delivery should be retried.
     */
    boolean deliver(WebhookDelivery delivery);
}
