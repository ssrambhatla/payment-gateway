package com.gateway.tokenization;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory token vault (practice only — a real vault is a separate,
 * hardened service). Tokens are random and carry no information about the
 * underlying value.
 */
@Component
public class InMemoryTokenVault implements TokenVault {

    private record VaultEntry(String merchantId, String sensitiveValue) {
    }

    private final Map<String, VaultEntry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String tokenize(String merchantId, String sensitiveValue) {
        if (sensitiveValue == null || sensitiveValue.isBlank()) {
            throw new IllegalArgumentException("Cannot tokenize an empty value");
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = "tok_" + HexFormat.of().formatHex(bytes);
        store.put(token, new VaultEntry(merchantId, sensitiveValue));
        return token;
    }

    @Override
    public String detokenize(String token) {
        VaultEntry entry = store.get(token);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown token");
        }
        return entry.sensitiveValue();
    }

    @Override
    public String last4(String sensitiveValue) {
        String digits = sensitiveValue.replaceAll("\\D", "");
        String source = digits.isEmpty() ? sensitiveValue : digits;
        return source.length() <= 4 ? source : source.substring(source.length() - 4);
    }
}
