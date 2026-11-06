package com.gateway.adapters;

import com.gateway.tokenization.TokenVault;
import org.springframework.stereotype.Component;

/**
 * Second mock PSP (M2): proves the framework works with more than one
 * provider. Same behavior as {@link MockPspAdapter} (including the
 * failover-drill kill switch) with its own name and PSP-reference prefix.
 * Adding it required no core change — one class + one config entry
 * ({@code gateway.routing.US.secondary: mock-psp-2}).
 */
@Component
public class SecondMockPspAdapter extends MockPspAdapter {

    public SecondMockPspAdapter(TokenVault vault) {
        super(vault);
    }

    @Override
    public String name() {
        return "mock-psp-2";
    }

    @Override
    protected String referencePrefix() {
        return "mock2_psp_";
    }
}
