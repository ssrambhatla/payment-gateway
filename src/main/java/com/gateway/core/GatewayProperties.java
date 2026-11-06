package com.gateway.core;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway configuration (see application.yml).
 *
 * <p>Routing is config-driven: each country maps to a primary + secondary
 * PSP, a traffic split, and optional overrides (amount threshold, payment
 * methods that always go to the secondary). No code change is needed to
 * re-route traffic.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        String defaultPsp,
        String settlementCurrency,
        String webhookSecret,
        Webhook webhook,
        Ach ach,
        Map<String, CountryRoute> routing) {

    /** Webhook delivery settings. */
    public record Webhook(int maxAttempts, long backoffBaseMs) {
    }

    /** ACH settlement timing (mock batch delay). */
    public record Ach(long settlementDelayMs) {
    }

    /** One country's routing rule. */
    public record CountryRoute(
            String primary,
            String secondary,
            Integer primaryPercent,
            Long amountThresholdMinor,
            List<PaymentMethod> secondaryMethods) {

        public CountryRoute {
            if (primaryPercent == null) {
                primaryPercent = 100;
            }
            if (secondaryMethods == null) {
                secondaryMethods = List.of();
            }
        }
    }

    public GatewayProperties {
        if (defaultPsp == null || defaultPsp.isBlank()) {
            defaultPsp = "mock-psp";
        }
        if (settlementCurrency == null || settlementCurrency.isBlank()) {
            settlementCurrency = "USD";
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            webhookSecret = "whsec_test_local_only";
        }
        if (webhook == null) {
            webhook = new Webhook(3, 1000L);
        }
        if (ach == null) {
            ach = new Ach(172800000L);
        }
        if (routing == null) {
            routing = Map.of();
        }
    }
}
