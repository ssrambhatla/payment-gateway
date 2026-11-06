package com.gateway.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Routing stage of the payment pipeline (M2).
 *
 * <p>Selects a PSP from the config-driven country rule ({@code gateway.routing}),
 * then calls PSPs in order until one succeeds:
 *
 * <ul>
 *   <li><b>Method override</b> — a payment method listed in
 *       {@code secondary-methods} always goes to the secondary PSP.</li>
 *   <li><b>Amount threshold</b> — amounts at or above
 *       {@code amount-threshold-minor} go to the secondary PSP.</li>
 *   <li><b>Percentage split</b> — otherwise {@code primary-percent}% of
 *       payments go to the primary; the rest to the secondary. The split is
 *       deterministic per payment id (hash-based), not random.</li>
 *   <li><b>Failover</b> — if the selected PSP fails (or throws), the router
 *       tries the other PSP. A PSP whose circuit breaker is OPEN is skipped
 *       outright.</li>
 * </ul>
 *
 * <p>New PSPs need no router change: register the adapter bean and reference
 * its name in config.
 */
@Component
public class PspRouter {

    private final Map<String, PSPAdapter> pspAdapters;
    private final GatewayProperties properties;
    private final PspCircuitBreaker breaker;

    public PspRouter(List<PSPAdapter> pspAdapters, GatewayProperties properties,
                     PspCircuitBreaker breaker) {
        this.pspAdapters = pspAdapters.stream()
                .collect(Collectors.toMap(PSPAdapter::name, Function.identity()));
        this.properties = properties;
        this.breaker = breaker;
    }

    public AdapterResult authorize(AuthorizeCommand command) {
        return route(command.country(), command.method(), command.amountMinor(), command.paymentId(),
                psp -> psp.authorize(command.paymentToken(), command.amountMinor(), command.currency()));
    }

    public AdapterResult capture(CaptureCommand command) {
        return route(command.country(), command.method(), command.amountMinor(), command.paymentId(),
                psp -> psp.capture(command.pspReference(), command.amountMinor(), command.currency()));
    }

    public AdapterResult refund(RefundCommand command) {
        return route(command.country(), command.method(), command.amountMinor(), command.paymentId(),
                psp -> psp.refund(command.pspReference(), command.amountMinor(), command.currency()));
    }

    /**
     * Ordered PSP candidates for a payment: preferred first, fallback second.
     * Visible for tests.
     */
    List<String> candidatesFor(String country, PaymentMethod method, long amountMinor, UUID paymentId) {
        GatewayProperties.CountryRoute route =
                country == null ? null : properties.routing().get(country);
        String primary = route != null && route.primary() != null
                ? route.primary() : properties.defaultPsp();
        String secondary = route != null ? route.secondary() : null;

        boolean preferSecondary = false;
        if (secondary != null && route != null) {
            preferSecondary = route.secondaryMethods().contains(method)
                    || (route.amountThresholdMinor() != null && amountMinor >= route.amountThresholdMinor())
                    || Math.floorMod(paymentId.hashCode(), 100) >= route.primaryPercent();
        }

        List<String> ordered = new ArrayList<>();
        if (preferSecondary) {
            ordered.add(secondary);
            ordered.add(primary);
        } else {
            ordered.add(primary);
            if (secondary != null) {
                ordered.add(secondary);
            }
        }
        return ordered.stream().distinct().toList();
    }

    private AdapterResult route(String country, PaymentMethod method, long amountMinor, UUID paymentId,
                                Function<PSPAdapter, AdapterResult> call) {
        String lastFailure = null;
        for (String name : candidatesFor(country, method, amountMinor, paymentId)) {
            PSPAdapter psp = pspAdapters.get(name);
            if (psp == null) {
                lastFailure = "unknown_psp:" + name;
                continue;
            }
            if (!breaker.allowRequest(name)) {
                lastFailure = "circuit_open:" + name;
                continue;
            }
            try {
                AdapterResult result = call.apply(psp);
                if (result.success()) {
                    breaker.recordSuccess(name);
                    return result;
                }
                breaker.recordFailure(name);
                lastFailure = result.failureReason();
            } catch (RuntimeException e) {
                breaker.recordFailure(name);
                lastFailure = e.getMessage();
            }
        }
        return AdapterResult.failed(lastFailure != null ? lastFailure : "no_psp_available");
    }
}
