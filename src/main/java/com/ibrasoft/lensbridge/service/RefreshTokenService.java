package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.exception.RefreshTokenException;
import com.ibrasoft.lensbridge.model.auth.RefreshToken;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.auth.RefreshTokenRepository;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import com.ibrasoft.lensbridge.security.TokenHasher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${lensbridge.app.refreshTokenExpirationMs:604800000}") // 7 days default
    private long refreshTokenDurationMs;

    @Value("${lensbridge.app.maxRefreshTokensPerUser:5}")
    private int maxRefreshTokensPerUser;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * A freshly issued refresh token. The raw value exists only here, for handing to the client:
     * the entity carries just its hash, so the plaintext cannot be recovered afterwards.
     */
    public record IssuedToken(String rawToken, RefreshToken entity) {
    }

    /**
     * Create a new refresh token for a user
     */
    @Transactional
    public IssuedToken createRefreshToken(UUID userId) {
        // Clean up expired tokens first
        deleteExpiredTokensByUser(userId);

        // Check if user has too many active tokens
        long activeTokenCount = refreshTokenRepository.countByUser_IdAndRevokedFalse(userId);
        if (activeTokenCount >= maxRefreshTokensPerUser) {
            // Revoke oldest token
            refreshTokenRepository.findByUser_IdAndRevokedFalse(userId).stream()
                .min((t1, t2) -> t1.getCreatedDate().compareTo(t2.getCreatedDate()))
                .ifPresent(oldest -> refreshTokenRepository.revokeIfActive(oldest.getTokenHash()));
        }
        Instant now = Instant.now();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String rawToken = generateRefreshToken();
        RefreshToken refreshToken = RefreshToken.builder()
            .tokenHash(TokenHasher.sha256Hex(rawToken))
            .user(user)
            .expiryDate(now.plusSeconds(refreshTokenDurationMs / 1000))
            .createdDate(now)
            .lastUsedDate(now)
            .revoked(false)
            .build();

        return new IssuedToken(rawToken, refreshTokenRepository.save(refreshToken));
    }

    /**
     * Exchange a refresh token for a new one: the presented token is revoked and a replacement
     * issued, as one transaction.
     *
     * <p>Revocation is a conditional bulk update that must change exactly one row, so two
     * concurrent requests with the same token cannot both succeed. A token that is already
     * revoked is a replay (a stolen copy, or a client that kept using a rotated-out token), so
     * every token of that user is revoked and the caller must sign in again. Both replay paths
     * fail like an unknown token (401).
     *
     * <p>The revocations have to survive the exception that reports the failure, hence
     * {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public IssuedToken rotate(String rawToken) {
        String hash = TokenHasher.sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new RefreshTokenException(
                "Refresh token not found. Please login again.", HttpStatus.UNAUTHORIZED));
        UUID userId = token.getUserId();

        if (token.isRevoked()) {
            throw rejectReplay(userId);
        }
        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenException("Refresh token expired. Please login again.", HttpStatus.UNAUTHORIZED);
        }
        if (refreshTokenRepository.revokeIfActive(hash) != 1) {
            // Lost the race: another request revoked this token between our read and our update.
            throw rejectReplay(userId);
        }

        return createRefreshToken(userId);
    }

    private RefreshTokenException rejectReplay(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId);
        log.warn("Revoked refresh token presented again; revoked all refresh tokens for user {}", userId);
        return new RefreshTokenException("Refresh token revoked. Please login again.", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Revoke a refresh token given the raw value the client holds. Unknown tokens are ignored so
     * logout stays idempotent.
     */
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        refreshTokenRepository.revokeIfActive(TokenHasher.sha256Hex(rawToken));
    }

    /**
     * Revoke all refresh tokens for a user (useful for logout all devices)
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId);
    }

    /**
     * Delete expired tokens for a specific user. Revoked tokens are kept on purpose: presenting
     * one again is how rotation detects reuse, and the scheduled cleanup removes them later.
     */
    @Transactional
    public void deleteExpiredTokensByUser(UUID userId) {
        refreshTokenRepository.deleteExpiredForUser(userId, Instant.now());
    }

    /**
     * Generate a secure random refresh token
     */
    private String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Scheduled task to clean up expired tokens (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            Instant now = Instant.now();
            int expired = refreshTokenRepository.deleteExpired(now);

            // Clean up revoked tokens (older than 7 days)
            int revoked = refreshTokenRepository.deleteRevokedCreatedBefore(now.minus(Duration.ofDays(7)));

            log.info("Cleaned up {} expired and {} old revoked refresh tokens", expired, revoked);
        } catch (Exception e) {
            log.error("Failed to cleanup expired refresh tokens", e);
        }
    }
}
