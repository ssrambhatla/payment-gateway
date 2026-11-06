package com.gateway.tokenization;

/**
 * Mock token vault. Raw card/bank values are tokenized at payment creation
 * and never stored anywhere else. Tokens are merchant-scoped.
 *
 * <p>Only PSP adapters may call {@link #detokenize(String)} — and even they
 * must not store or log the value. The M1 mock PSP detokenizes only to prove
 * the token resolves, then discards the value.
 */
public interface TokenVault {

    /** Stores the sensitive value, returns an opaque token (never the value itself). */
    String tokenize(String merchantId, String sensitiveValue);

    /** Resolves a token. PSP adapters only. */
    String detokenize(String token);

    /** Last 4 characters of the sensitive value, for display (e.g. card last4). */
    String last4(String sensitiveValue);
}
