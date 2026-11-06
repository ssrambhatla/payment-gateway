package com.gateway.adapters;

import com.gateway.core.AdapterResult;
import com.gateway.core.AuthorizeCommand;
import com.gateway.core.CaptureCommand;
import com.gateway.core.PaymentMethodAdapter;
import com.gateway.core.PspRouter;
import com.gateway.core.RefundCommand;

/**
 * Base for method adapters: delegates to the routing stage, which picks the
 * PSP from config and fails over. Method adapters never pick a PSP directly.
 */
abstract class DelegatingMethodAdapter implements PaymentMethodAdapter {

    private final PspRouter router;

    protected DelegatingMethodAdapter(PspRouter router) {
        this.router = router;
    }

    @Override
    public AdapterResult authorize(AuthorizeCommand command) {
        return router.authorize(command);
    }

    @Override
    public AdapterResult capture(CaptureCommand command) {
        return router.capture(command);
    }

    @Override
    public AdapterResult refund(RefundCommand command) {
        return router.refund(command);
    }
}
