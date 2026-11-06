package com.gateway.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.gateway.core.PaymentMethod;
import com.gateway.domain.IdempotencyRecord;
import com.gateway.domain.IdempotencyRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency for payment creation (M2). A merchant sends
 * {@code Idempotency-Key} with a creation request; a retry with the same key
 * and the same payload returns the original payment instead of charging
 * twice. The same key with a different payload is a 409 conflict.
 *
 * <p>The fingerprint is a double hash: the sensitive value is hashed first,
 * then the canonical request string is hashed — no PAN or bank value is ever
 * stored in the record.
 */
@Component
public class IdempotencyService {

    private final IdempotencyRecordRepository records;

    public IdempotencyService(IdempotencyRecordRepository records) {
        this.records = records;
    }

    /** Canonical fingerprint of a creation request (hex SHA-256). */
    public String fingerprint(PaymentMethod method, long amountMinor, String currency,
                              String sensitiveValue) {
        String sensitiveHash = sha256Hex(sensitiveValue == null ? "" : sensitiveValue);
        String canonical = method.name() + "|" + amountMinor + "|" + currency + "|" + sensitiveHash;
        return sha256Hex(canonical);
    }

    public Optional<IdempotencyRecord> find(UUID merchantId, String key) {
        return records.findByMerchantIdAndIdempotencyKey(merchantId, key);
    }

    @Transactional
    public void record(UUID merchantId, String key, String fingerprint, UUID paymentId) {
        records.save(new IdempotencyRecord(UUID.randomUUID(), merchantId, key,
                fingerprint, paymentId, Instant.now()));
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
