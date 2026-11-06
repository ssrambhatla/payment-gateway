package com.gateway.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<WebhookDelivery> findByStatusAndNextAttemptAtBefore(
            WebhookDelivery.Status status, Instant before);
}
