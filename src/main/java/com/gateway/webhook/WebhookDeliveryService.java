package com.gateway.webhook;

import java.util.List;
import java.util.UUID;

import com.gateway.domain.WebhookDelivery;
import com.gateway.domain.WebhookDeliveryRepository;
import org.springframework.stereotype.Service;

/** Read side for webhook deliveries (merchant-scoped). */
@Service
public class WebhookDeliveryService {

    private final WebhookDeliveryRepository deliveries;

    public WebhookDeliveryService(WebhookDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    public List<WebhookDelivery> listForMerchant(UUID merchantId) {
        return deliveries.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    public WebhookDelivery getForMerchant(UUID merchantId, UUID deliveryId) {
        return deliveries.findById(deliveryId)
                .filter(d -> d.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Webhook delivery not found: " + deliveryId));
    }
}
