package com.gateway.adapters;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.gateway.core.AdapterResult;
import com.gateway.core.PSPAdapter;
import com.gateway.tokenization.TokenVault;
import org.springframework.stereotype.Component;

/**
 * Mock PSP (M1 success path). Simulates a provider: accepts a token, returns
 * a PSP reference. It detokenizes only to prove the token resolves — the
 * value is discarded immediately, never stored or logged.
 *
 * <p>M2: a kill switch ({@link #setSimulateFailure(boolean)}) makes this PSP
 * fail every call, so the failover drill can prove traffic moves to the
 * secondary PSP. The switch is mock-only and off by default.
 */
@Component
public class MockPspAdapter implements PSPAdapter {

    private final TokenVault vault;
    private final AtomicBoolean simulateFailure = new AtomicBoolean(false);

    public MockPspAdapter(TokenVault vault) {
        this.vault = vault;
    }

    @Override
    public String name() {
        return "mock-psp";
    }

    /** Reference prefix, overridden by the second mock PSP. */
    protected String referencePrefix() {
        return "mock_psp_";
    }

    /** Failover drill only: when true, every call fails. Default false. */
    public void setSimulateFailure(boolean value) {
        simulateFailure.set(value);
    }

    public boolean isSimulateFailure() {
        return simulateFailure.get();
    }

    @Override
    public AdapterResult authorize(String paymentToken, long amountMinor, String currency) {
        if (simulateFailure.get()) {
            return AdapterResult.failed("simulated_failure");
        }
        // Detokenize only to validate the token resolves; value is discarded.
        String resolved = vault.detokenize(paymentToken);
        if (resolved == null || resolved.isBlank()) {
            return AdapterResult.failed("invalid_token");
        }
        return AdapterResult.ok(referencePrefix() + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }

    @Override
    public AdapterResult capture(String pspReference, long amountMinor, String currency) {
        if (simulateFailure.get()) {
            return AdapterResult.failed("simulated_failure");
        }
        if (pspReference == null || pspReference.isBlank()) {
            return AdapterResult.failed("missing_psp_reference");
        }
        return AdapterResult.ok(pspReference);
    }

    @Override
    public AdapterResult refund(String pspReference, long amountMinor, String currency) {
        if (simulateFailure.get()) {
            return AdapterResult.failed("simulated_failure");
        }
        if (pspReference == null || pspReference.isBlank()) {
            return AdapterResult.failed("missing_psp_reference");
        }
        return AdapterResult.ok(pspReference);
    }
}
