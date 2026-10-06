package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import com.ibrasoft.lensbridge.repository.sql.EnrollmentTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and consumes one-time enrollment tokens.
 * <p>
 * The plaintext token is returned to the admin once at issue time and is never persisted —
 * we only store its SHA-256 hash. Consumption is single-attempt: it is one conditional
 * {@code UPDATE ... WHERE consumed_at IS NULL AND expires_at > now} and succeeds only when
 * exactly one row changed. The database decides the winner, so two concurrent agents racing
 * on the same token cannot both enroll (a read-check-write in Java would let both through).
 */
@Service
@RequiredArgsConstructor
public class EnrollmentTokenService {

    /** 18 random bytes → 24 chars of url-safe base64 with no padding. ~144 bits of entropy. */
    private static final int TOKEN_RANDOM_BYTES = 18;

    private static final int DEFAULT_TTL_MINUTES = 30;
    private static final int MAX_TTL_MINUTES = 60 * 24;

    private final EnrollmentTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    @Value("${musallahboard.enrollment.tokenPepper:}")
    private String pepper;

    public record Issued(EnrollmentToken token, String plaintext) {}

    @Transactional
    public Issued issue(String displayName, Audience audience, Integer ttlMinutes, String createdBy) {
        int ttl = clampTtl(ttlMinutes);
        String plaintext = generatePlaintext();

        EnrollmentToken row = EnrollmentToken.builder()
                .tokenHash(hash(plaintext))
                .displayName(displayName)
                .audience(audience)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(ttl)))
                .build();

        EnrollmentToken saved = repository.save(row);
        return new Issued(saved, plaintext);
    }

    /**
     * Atomically marks a token as consumed iff it is unconsumed and unexpired.
     * <p>
     * The device id is not known yet at this point (Hibernate generates it when the device is
     * persisted), so the caller records it afterwards with {@link #recordConsumer}; both run in
     * the enrollment transaction, so a failure in between rolls the consumption back too.
     *
     * @return the consumed row on success, empty if the token is unknown / expired / already used
     */
    @Transactional
    public Optional<EnrollmentToken> consume(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return Optional.empty();

        Optional<EnrollmentToken> found = repository.findByTokenHash(hash(plaintext));
        if (found.isEmpty()) return Optional.empty();

        EnrollmentToken row = found.get();
        Instant now = Instant.now();
        // Exactly one concurrent caller sees a row count of 1; everyone else sees 0.
        if (repository.markConsumed(row.getId(), now) != 1) return Optional.empty();

        // markConsumed detaches the row it loaded, so this only fixes up the copy we return.
        row.setConsumedAt(now);
        return Optional.of(row);
    }

    /** Records which device a just-consumed token produced. */
    @Transactional
    public void recordConsumer(UUID tokenId, UUID deviceId) {
        repository.recordConsumer(tokenId, deviceId);
    }

    private int clampTtl(Integer requested) {
        if (requested == null) return DEFAULT_TTL_MINUTES;
        return Math.min(MAX_TTL_MINUTES, Math.max(1, requested));
    }

    private String generatePlaintext() {
        byte[] raw = new byte[TOKEN_RANDOM_BYTES];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    byte[] hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (pepper != null && !pepper.isEmpty()) {
                digest.update(pepper.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return digest.digest(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
