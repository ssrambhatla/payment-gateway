package com.gateway.core;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Plugin registry. All adapters are Spring beans; the registry indexes them
 * by payment method / PSP name at startup. Config (gateway.default-psp)
 * selects the PSP used when no routing rule overrides it (routing rules
 * arrive in M2). To add a new payment method or PSP: add the adapter bean,
 * nothing else changes.
 */
@Component
public class AdapterRegistry {

    private final Map<PaymentMethod, PaymentMethodAdapter> methodAdapters;
    private final Map<String, PSPAdapter> pspAdapters;
    private final GatewayProperties properties;

    public AdapterRegistry(List<PaymentMethodAdapter> methodAdapters,
                           List<PSPAdapter> pspAdapters,
                           GatewayProperties properties) {
        this.methodAdapters = methodAdapters.stream()
                .collect(Collectors.toMap(PaymentMethodAdapter::method, Function.identity()));
        this.pspAdapters = pspAdapters.stream()
                .collect(Collectors.toMap(PSPAdapter::name, Function.identity()));
        this.properties = properties;
    }

    public PaymentMethodAdapter methodAdapterFor(PaymentMethod method) {
        PaymentMethodAdapter adapter = methodAdapters.get(method);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for payment method: " + method);
        }
        return adapter;
    }

    public PSPAdapter pspAdapter(String name) {
        PSPAdapter adapter = pspAdapters.get(name);
        if (adapter == null) {
            throw new IllegalArgumentException("No PSP adapter registered with name: " + name);
        }
        return adapter;
    }

    /** The PSP used when no routing rule overrides it. */
    public PSPAdapter defaultPspAdapter() {
        return pspAdapter(properties.defaultPsp());
    }
}
