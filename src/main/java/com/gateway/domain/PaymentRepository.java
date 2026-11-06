package com.gateway.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.gateway.core.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Payments in a status whose settlement due date has passed (batch job). */
    List<Payment> findByStatusAndSettlementDueAtBefore(PaymentStatus status, Instant before);
}
