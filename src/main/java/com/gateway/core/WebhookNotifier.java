package com.gateway.core;

import com.gateway.domain.Payment;

/**
 * Outbound merchant notification hook. M2 implements it with signed webhook
 * delivery + retries (see WebhookDispatcher); the transport underneath is
 * pluggable (logging in M2, real HTTP later).
 */
public interface WebhookNotifier {

    void notify(Payment payment, String event);
}
