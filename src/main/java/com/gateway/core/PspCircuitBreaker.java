package com.gateway.core;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Per-PSP circuit breaker (in-memory, practice scope).
 *
 * <p>States: CLOSED (traffic flows) → OPEN after {@value #FAILURE_THRESHOLD}
 * consecutive failures (traffic skips this PSP for {@value #COOLDOWN_MS} ms)
 * → HALF_OPEN (exactly one probe request allowed; success closes it, failure
 * re-opens it). The router consults the breaker before every PSP call, so a
 * dead PSP fails over fast instead of timing out every request.
 */
@Component
public class PspCircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    static final int FAILURE_THRESHOLD = 3;
    static final long COOLDOWN_MS = 30_000L;

    private record Breaker(int consecutiveFailures, long openUntilMs, State state, boolean probeAvailable) {
    }

    private final ConcurrentHashMap<String, Breaker> breakers = new ConcurrentHashMap<>();

    /** True when the PSP may receive a request right now. */
    public synchronized boolean allowRequest(String pspName) {
        Breaker breaker = breakers.getOrDefault(pspName, new Breaker(0, 0L, State.CLOSED, false));
        switch (breaker.state()) {
            case CLOSED -> {
                return true;
            }
            case OPEN -> {
                if (System.currentTimeMillis() >= breaker.openUntilMs()) {
                    breakers.put(pspName,
                            new Breaker(breaker.consecutiveFailures(), 0L, State.HALF_OPEN, true));
                    return true;
                }
                return false;
            }
            case HALF_OPEN -> {
                if (breaker.probeAvailable()) {
                    breakers.put(pspName,
                            new Breaker(breaker.consecutiveFailures(), 0L, State.HALF_OPEN, false));
                    return true;
                }
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    /** A successful call resets the breaker to CLOSED. */
    public synchronized void recordSuccess(String pspName) {
        breakers.put(pspName, new Breaker(0, 0L, State.CLOSED, false));
    }

    /** A failed call counts toward the threshold; reaching it opens the breaker. */
    public synchronized void recordFailure(String pspName) {
        Breaker breaker = breakers.getOrDefault(pspName, new Breaker(0, 0L, State.CLOSED, false));
        if (breaker.state() == State.HALF_OPEN) {
            // Probe failed: back to OPEN for another cooldown.
            breakers.put(pspName,
                    new Breaker(FAILURE_THRESHOLD, System.currentTimeMillis() + COOLDOWN_MS, State.OPEN, false));
            return;
        }
        int failures = breaker.consecutiveFailures() + 1;
        if (failures >= FAILURE_THRESHOLD) {
            breakers.put(pspName,
                    new Breaker(failures, System.currentTimeMillis() + COOLDOWN_MS, State.OPEN, false));
        } else {
            breakers.put(pspName, new Breaker(failures, 0L, State.CLOSED, false));
        }
    }

    public synchronized State state(String pspName) {
        Breaker breaker = breakers.getOrDefault(pspName, new Breaker(0, 0L, State.CLOSED, false));
        if (breaker.state() == State.OPEN && System.currentTimeMillis() >= breaker.openUntilMs()) {
            return State.HALF_OPEN;
        }
        return breaker.state();
    }

    /** Test/drill support: force a breaker back to CLOSED. */
    public synchronized void reset(String pspName) {
        breakers.remove(pspName);
    }

    /** Test/drill support: reset every breaker. */
    public synchronized void resetAll() {
        breakers.clear();
    }
}
