package com.gateway.adapters;

import com.gateway.core.PaymentMethod;
import com.gateway.core.PspRouter;
import org.springframework.stereotype.Component;

/**
 * ACH adapter (M2): authorizes through the routing stage like a card. The
 * ACH shape lives in the settlement layer, not the adapter — an authorized
 * ACH payment is captured, then settles after the mock batch delay
 * (see SettlementService), instead of settling instantly like IBP.
 */
@Component
public class AchPaymentAdapter extends DelegatingMethodAdapter {

    public AchPaymentAdapter(PspRouter router) {
        super(router);
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.ACH;
    }
}
