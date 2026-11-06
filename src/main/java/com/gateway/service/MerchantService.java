package com.gateway.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.gateway.domain.Merchant;
import com.gateway.domain.MerchantRepository;
import org.springframework.stereotype.Service;

/**
 * Merchant onboarding + API key validation. Keys are practice-only
 * (sk_test_ prefix), generated once at creation and returned to the caller.
 * Keys are never logged.
 */
@Service
public class MerchantService {

    private final MerchantRepository repository;
    private final SecureRandom random = new SecureRandom();

    public MerchantService(MerchantRepository repository) {
        this.repository = repository;
    }

    public Merchant createMerchant(String name) {
        String apiKey = "sk_test_" + HexFormat.of().formatHex(randomBytes(24));
        Merchant merchant = new Merchant(UUID.randomUUID(), name, apiKey, Instant.now());
        return repository.save(merchant);
    }

    public Optional<Merchant> findByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByApiKey(apiKey);
    }

    private byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }
}
