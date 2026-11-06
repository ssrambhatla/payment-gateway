package com.gateway.api;

import java.util.List;
import java.util.UUID;

import com.gateway.webhook.WebhookDeliveryService;
import com.gateway.webhook.WebhookDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook delivery tracking (M2). Merchants list their deliveries and can
 * manually retry a failed one (the merchant test endpoint).
 */
@RestController
@RequestMapping("/v1/webhooks")
public class WebhookController {

    private final WebhookDeliveryService deliveries;
    private final WebhookDispatcher dispatcher;

    public WebhookController(WebhookDeliveryService deliveries, WebhookDispatcher dispatcher) {
        this.deliveries = deliveries;
        this.dispatcher = dispatcher;
    }

    @GetMapping("/deliveries")
    public List<WebhookDeliveryResponse> list(HttpServletRequest http) {
        return deliveries.listForMerchant(merchantId(http)).stream()
                .map(WebhookDeliveryResponse::from)
                .toList();
    }

    @GetMapping("/deliveries/{id}")
    public WebhookDeliveryResponse get(@PathVariable UUID id, HttpServletRequest http) {
        return WebhookDeliveryResponse.from(deliveries.getForMerchant(merchantId(http), id));
    }

    /** Manual retry of a failed delivery (resets the attempt count). */
    @PostMapping("/deliveries/{id}/retry")
    public WebhookDeliveryResponse retry(@PathVariable UUID id, HttpServletRequest http) {
        return WebhookDeliveryResponse.from(dispatcher.retry(merchantId(http), id));
    }

    private UUID merchantId(HttpServletRequest http) {
        return (UUID) http.getAttribute(ApiKeyAuthFilter.MERCHANT_ID_ATTRIBUTE);
    }
}
