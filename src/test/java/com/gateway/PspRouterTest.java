package com.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gateway.adapters.MockPspAdapter;
import com.gateway.adapters.SecondMockPspAdapter;
import com.gateway.core.AdapterResult;
import com.gateway.core.AuthorizeCommand;
import com.gateway.core.CaptureCommand;
import com.gateway.core.GatewayProperties;
import com.gateway.core.PaymentMethod;
import com.gateway.core.PaymentStatus;
import com.gateway.core.PspCircuitBreaker;
import com.gateway.core.PspRouter;
import com.gateway.core.RefundCommand;
import com.gateway.domain.Payment;
import com.gateway.tokenization.InMemoryTokenVault;
import org.junit.jupiter.api.Test;

/**
 * M2 routing unit tests: failover, traffic split, amount threshold, method
 * override, and the per-PSP circuit breaker.
 */
class PspRouterTest {

    private static final InMemoryTokenVault VAULT = new InMemoryTokenVault();
    private static final String TOKEN = VAULT.tokenize("test-merchant", "4242424242424242");

    private static Payment payment(UUID id, PaymentMethod method, long amountMinor) {
        Payment p = new Payment(id, UUID.randomUUID(), method, amountMinor, "USD",
                PaymentStatus.PENDING, "tok_test", "4242", Instant.now(), Instant.now());
        p.setCountry("US");
        return p;
    }

    private static GatewayProperties properties(GatewayProperties.CountryRoute route) {
        return new GatewayProperties("mock-psp", "USD", "whsec_test",
                new GatewayProperties.Webhook(3, 1000L),
                new GatewayProperties.Ach(172800000L),
                Map.of("US", route));
    }

    private static GatewayProperties.CountryRoute route(int primaryPercent,
                                                        Long amountThresholdMinor,
                                                        List<PaymentMethod> secondaryMethods) {
        return new GatewayProperties.CountryRoute("mock-psp", "mock-psp-2",
                primaryPercent, amountThresholdMinor, secondaryMethods);
    }

    private static GatewayProperties.CountryRoute routeNoSecondary() {
        return new GatewayProperties.CountryRoute("mock-psp", null, 100, null, List.of());
    }

    private static PspRouter router(GatewayProperties props, MockPspAdapter... adapters) {
        return new PspRouter(List.of(adapters), props, new PspCircuitBreaker());
    }

    private static AuthorizeCommand command(Payment payment) {
        return new AuthorizeCommand(payment.getMerchantId().toString(), TOKEN,
                payment.getAmountMinor(), payment.getCurrency(), payment.getCountry(),
                payment.getMethod(), payment.getId());
    }

    @Test
    void failoverToSecondaryWhenPrimaryFails() {
        MockPspAdapter primary = new MockPspAdapter(VAULT);
        primary.setSimulateFailure(true);
        MockPspAdapter secondary = new SecondMockPspAdapter(VAULT);
        PspRouter router = router(properties(route(100, null, List.of())), primary, secondary);

        AdapterResult result = router.authorize(command(payment(UUID.randomUUID(), PaymentMethod.CARD, 5000)));

        assertTrue(result.success());
        assertTrue(result.pspReference().startsWith("mock2_psp_"),
                "Failed primary must route to the secondary PSP, got: " + result.pspReference());
    }

    @Test
    void percentSplitZeroAlwaysGoesToSecondary() {
        MockPspAdapter primary = new MockPspAdapter(VAULT);
        MockPspAdapter secondary = new SecondMockPspAdapter(VAULT);
        PspRouter router = router(properties(route(0, null, List.of())), primary, secondary);

        AdapterResult result = router.authorize(command(payment(UUID.randomUUID(), PaymentMethod.CARD, 5000)));

        assertTrue(result.success());
        assertTrue(result.pspReference().startsWith("mock2_psp_"));
    }

    @Test
    void percentSplitHundredAlwaysGoesToPrimary() {
        MockPspAdapter primary = new MockPspAdapter(VAULT);
        MockPspAdapter secondary = new SecondMockPspAdapter(VAULT);
        PspRouter router = router(properties(route(100, null, List.of())), primary, secondary);

        AdapterResult result = router.authorize(command(payment(UUID.randomUUID(), PaymentMethod.CARD, 5000)));

        assertTrue(result.success());
        assertTrue(result.pspReference().startsWith("mock_psp_"));
        assertTrue(!result.pspReference().startsWith("mock2_psp_"));
    }

    @Test
    void percentSplitFollowsTheDeterministicBucket() {
        MockPspAdapter primary = new MockPspAdapter(VAULT);
        MockPspAdapter secondary = new SecondMockPspAdapter(VAULT);
        PspRouter router = router(properties(route(50, null, List.of())), primary, secondary);

        UUID id = UUID.randomUUID();
        boolean expectSecondary = Math.floorMod(id.hashCode(), 100) >= 50;
        AdapterResult result = router.authorize(command(payment(id, PaymentMethod.CARD, 5000)));

        assertTrue(result.success());
        if (expectSecondary) {
            assertTrue(result.pspReference().startsWith("mock2_psp_"));
        } else {
            assertTrue(result.pspReference().startsWith("mock_psp_"));
        }
    }

    @Test
    void amountThresholdRoutesLargePaymentsToSecondary() {
        MockPspAdapter primary = new MockPspAdapter(VAULT);
        MockPspAdapter secondary = new SecondMockPspAdapter(VAULT);
        PspRouter router = router(properties(route(100, 100000L, List.of())), primary, secondary);

        AdapterResult small = router.authorize(command(payment(UUID.randomUUID(), PaymentMethod.CARD, 99999)));
        AdapterResult large = router.authorize(command(payment(UUID.randomUUID(), PaymentMethod.CARD, 100000)));

        assertTrue(small.pspReference().startsWith("mock_psp_"));
        assertTrue(large.pspReference().startsWith("mock2_psp_"));
    }

    @Test
    void methodOverrideRoutesAchToSecondary() {
        MockPspAdapter primary = new MockPspAdapter(VAULT);
        MockPspAdapter secondary = new SecondMockPspAdapter(VAULT);
        PspRouter router = router(properties(route(100, null, List.of(PaymentMethod.ACH))), primary, secondary);

        AdapterResult ach = router.authorize(command(payment(UUID.randomUUID(), PaymentMethod.ACH, 5000)));
        AdapterResult card = router.authorize(command(payment(UUID.randomUUID(), PaymentMethod.CARD, 5000)));

        assertTrue(ach.pspReference().startsWith("mock2_psp_"));
        assertTrue(card.pspReference().startsWith("mock_psp_"));
    }

    @Test
    void circuitBreakerOpensAfterThreeConsecutiveFailures() {
        MockPspAdapter failing = new MockPspAdapter(VAULT);
        failing.setSimulateFailure(true);
        PspCircuitBreaker breaker = new PspCircuitBreaker();
        PspRouter router = new PspRouter(List.of(failing), properties(routeNoSecondary()), breaker);

        AuthorizeCommand cmd = command(payment(UUID.randomUUID(), PaymentMethod.CARD, 5000));
        for (int i = 0; i < 3; i++) {
            AdapterResult failed = router.authorize(cmd);
            assertTrue(!failed.success(), "Simulated failure must fail");
        }
        assertEquals(PspCircuitBreaker.State.OPEN, breaker.state("mock-psp"),
                "Breaker must open after 3 consecutive failures");

        // Breaker open: the router refuses to call the PSP at all.
        AdapterResult refused = router.authorize(cmd);
        assertTrue(!refused.success());
        assertTrue(refused.failureReason().contains("circuit_open"));

        // A success resets it (e.g. after a half-open probe succeeds).
        breaker.recordSuccess("mock-psp");
        assertEquals(PspCircuitBreaker.State.CLOSED, breaker.state("mock-psp"));
    }

    @Test
    void captureAndRefundRouteThroughTheSameRouter() {
        MockPspAdapter primary = new MockPspAdapter(VAULT);
        MockPspAdapter secondary = new SecondMockPspAdapter(VAULT);
        PspRouter router = router(properties(route(100, null, List.of())), primary, secondary);
        UUID id = UUID.randomUUID();
        Payment payment = payment(id, PaymentMethod.CARD, 5000);
        String merchantId = payment.getMerchantId().toString();

        AdapterResult captured = router.capture(
                new CaptureCommand(merchantId, "mock_psp_ref", 5000, "USD", "US",
                        PaymentMethod.CARD, id));
        assertTrue(captured.success());
        AdapterResult refunded = router.refund(
                new RefundCommand(merchantId, "mock_psp_ref", 2000, "USD", "US",
                        PaymentMethod.CARD, id));
        assertTrue(refunded.success());
    }
}
