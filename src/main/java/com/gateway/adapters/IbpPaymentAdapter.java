package com.gateway.adapters;

import com.gateway.core.PaymentMethod;
import com.gateway.core.PspRouter;
import org.springframework.stereotype.Component;

/**
 * IBP (Instant Bank Payment) adapter (M2): instant confirmation through the
 * routing stage, no batch delay (unlike ACH). An IBP payment settles
 * immediately at capture (see SettlementService).
 */
@Component
public class IbpPaymentAdapter extends DelegatingMethodAdapter {

    public IbpPaymentAdapter(PspRouter router) {
        super(router);
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.IBP;
    }
}
