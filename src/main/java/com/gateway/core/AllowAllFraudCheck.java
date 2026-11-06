package com.gateway.core;

import com.gateway.domain.Payment;
import org.springframework.stereotype.Component;

/** M1 fraud hook: approves everything. Real checks plug in here later. */
@Component
public class AllowAllFraudCheck implements FraudCheck {

    @Override
    public boolean approve(Payment payment) {
        return true;
    }
}
