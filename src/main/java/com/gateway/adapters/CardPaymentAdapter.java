package com.gateway.adapters;

import com.gateway.core.PaymentMethod;
import com.gateway.core.PspRouter;
import org.springframework.stereotype.Component;

/**
 * Card adapter: authorize → capture → refund via the routing stage, which
 * picks the PSP from config and fails over. Full flow in M1; routing in M2.
 */
@Component
public class CardPaymentAdapter extends DelegatingMethodAdapter {

    public CardPaymentAdapter(PspRouter router) {
        super(router);
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }
}
