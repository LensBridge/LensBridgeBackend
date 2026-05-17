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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
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

    public void invalidateAllRefreshTokensForUser(UUID userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUser_IdAndRevokedFalse(userId);
        tokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }

    /**
     * Create a new refresh token for a user
     */
    @Transactional
    public RefreshToken createRefreshToken(UUID userId, String deviceInfo, String ipAddress) {
        // Clean up expired tokens first
        deleteExpiredTokensByUser(userId);
        
        // Check if user has too many active tokens
        long activeTokenCount = refreshTokenRepository.countByUser_IdAndRevokedFalse(userId);
        if (activeTokenCount >= maxRefreshTokensPerUser) {
            // Revoke oldest token
            List<RefreshToken> userTokens = refreshTokenRepository.findByUser_IdAndRevokedFalse(userId);
            if (!userTokens.isEmpty()) {
                RefreshToken oldestToken = userTokens.stream()
                    .min((t1, t2) -> t1.getCreatedDate().compareTo(t2.getCreatedDate()))
                    .orElse(null);
                if (oldestToken != null) {
                    revokeRefreshToken(oldestToken.getTokenHash());
                }
            }
        }
        Instant now = Instant.now();
        
        RefreshToken refreshToken = RefreshToken.builder()
            .tokenHash(generateRefreshToken())
            .user(userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found")))
            .expiryDate(now.plusSeconds(refreshTokenDurationMs / 1000))
            .createdDate(now)
            .lastUsedDate(now)
            .revoked(false)
            .build();
        
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Find refresh token by token string
     */
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByTokenHash(token);
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
     * Revoke a refresh token
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByTokenHash(token).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
        });
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
