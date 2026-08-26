package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.exception.RefreshTokenException;
import com.ibrasoft.lensbridge.model.auth.RefreshToken;
import com.ibrasoft.lensbridge.repository.auth.RefreshTokenRepository;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and redeems refresh tokens.
 * <p>
 * The plaintext token exists only in the response to the client that asked for it. What is
 * persisted is a SHA-256 digest of that value, so a read of {@code refresh_tokens} — a leaked
 * backup, a misconfigured replica, SQL injection elsewhere — yields nothing that can be replayed
 * against {@code /api/auth/refresh-token}. This mirrors {@link VerificationTokenService} and
 * {@code service.agent.EnrollmentTokenService}.
 * <p>
 * A plain digest (not bcrypt/argon2) is the right choice here: the input is 512 bits of
 * {@link SecureRandom} output, so there is no guessable password to slow an attacker down to, and
 * redemption has to stay a single indexed equality lookup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    /** 64 random bytes -> 86 chars of url-safe base64. 512 bits of entropy. */
    private static final int TOKEN_RANDOM_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${lensbridge.app.refreshTokenExpirationMs:604800000}") // 7 days default
    private long refreshTokenDurationMs;

    @Value("${lensbridge.app.maxRefreshTokensPerUser:5}")
    private int maxRefreshTokensPerUser;

    /** Optional extra input to the token hash. Empty disables it. */
    @Value("${lensbridge.app.refreshTokenPepper:}")
    private String pepper;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * A freshly minted token: the stored row, plus the plaintext to hand back to the client.
     * The plaintext is not recoverable from the row and is never persisted, so this is the only
     * point at which it is available.
     */
    public record IssuedRefreshToken(RefreshToken token, String plaintext) {
    }

    public void invalidateAllRefreshTokensForUser(UUID userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUser_IdAndRevokedFalse(userId);
        tokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }

    /**
     * Create a new refresh token for a user.
     *
     * @return the persisted row and the plaintext token; only the caller of this method ever sees
     *         the plaintext.
     */
    @Transactional
    public IssuedRefreshToken createRefreshToken(UUID userId, String deviceInfo, String ipAddress) {
        // Clean up expired tokens first
        deleteExpiredTokensByUser(userId);

        // Check if user has too many active tokens
        long activeTokenCount = refreshTokenRepository.countByUser_IdAndRevokedFalse(userId);
        if (activeTokenCount >= maxRefreshTokensPerUser) {
            // Revoke oldest token
            List<RefreshToken> userTokens = refreshTokenRepository.findByUser_IdAndRevokedFalse(userId);
            userTokens.stream()
                    .min((t1, t2) -> t1.getCreatedDate().compareTo(t2.getCreatedDate()))
                    .ifPresent(this::revokeToken);
        }
        Instant now = Instant.now();

        String plaintext = generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
            .tokenHash(resolveHash(plaintext))
            .user(userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found")))
            .expiryDate(now.plusSeconds(refreshTokenDurationMs / 1000))
            .createdDate(now)
            .lastUsedDate(now)
            .revoked(false)
            .build();

        return new IssuedRefreshToken(refreshTokenRepository.save(refreshToken), plaintext);
    }

    /**
     * Find a refresh token by the plaintext value the client presented.
     */
    public Optional<RefreshToken> findByToken(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(resolveHash(plaintextToken));
    }

    /**
     * Verify refresh token is valid and not expired
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isRevoked()) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenException("Refresh token revoked. Please login again.", HttpStatus.UNAUTHORIZED);
        }
        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenException("Refresh token expired. Please login again.", HttpStatus.UNAUTHORIZED);
        }

        token.setLastUsedDate(Instant.now());
        return refreshTokenRepository.save(token);
    }

    /**
     * Revoke a refresh token, given the plaintext value the client holds.
     */
    @Transactional
    public void revokeRefreshToken(String plaintextToken) {
        findByToken(plaintextToken).ifPresent(this::revokeToken);
    }

    /**
     * Revoke all refresh tokens for a user (useful for logout all devices)
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        List<RefreshToken> userTokens = refreshTokenRepository.findByUser_IdAndRevokedFalse(userId);
        userTokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(userTokens);
    }

    /**
     * Delete expired tokens for a specific user
     */
    @Transactional
    public void deleteExpiredTokensByUser(UUID userId) {
        List<RefreshToken> userTokens = refreshTokenRepository.findByUser_Id(userId);
        List<RefreshToken> expiredTokens = userTokens.stream()
            .filter(token -> token.isExpired() || token.isRevoked())
            .toList();

        if (!expiredTokens.isEmpty()) {
            refreshTokenRepository.deleteAll(expiredTokens);
        }
    }

    /**
     * Get all active refresh tokens for a user (for security dashboard)
     */
    public List<RefreshToken> getActiveTokensForUser(UUID userId) {
        return refreshTokenRepository.findByUser_IdAndRevokedFalse(userId);
    }

    private void revokeToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    /**
     * Generate a secure random refresh token
     */
    private String generateRefreshToken() {
        byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * SHA-256 of the plaintext, hex-encoded, optionally peppered. 64 chars, so it fits the
     * existing {@code varchar(255)} column.
     */
    String resolveHash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (pepper != null && !pepper.isEmpty()) {
                digest.update(pepper.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Scheduled task to clean up expired tokens (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            Instant now = Instant.now();
            refreshTokenRepository.deleteByExpiryDateBefore(now);

            // Clean up revoked tokens (older than 7 days)
            List<RefreshToken> oldRevokedTokens = refreshTokenRepository
                .findByRevokedTrueAndCreatedDateBefore(now.minus(Duration.ofDays(7)));

            if (!oldRevokedTokens.isEmpty()) {
                refreshTokenRepository.deleteAll(oldRevokedTokens);
                log.info("Cleaned up {} old revoked refresh tokens", oldRevokedTokens.size());
            }

            log.info("Cleaned up expired refresh tokens");
        } catch (Exception e) {
            log.error("Failed to cleanup expired refresh tokens", e);
        }
    }
}
