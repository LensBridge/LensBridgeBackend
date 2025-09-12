package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.auth.RefreshToken;
import com.ibrasoft.lensbridge.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    
    @Value("${lensbridge.app.refreshTokenExpirationMs:604800000}") // 7 days default
    private long refreshTokenDurationMs;
    
    @Value("${lensbridge.app.maxRefreshTokensPerUser:5}")
    private int maxRefreshTokensPerUser;
    
    private final SecureRandom secureRandom = new SecureRandom();

    public void invalidateAllRefreshTokensForUser(UUID userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
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
        long activeTokenCount = refreshTokenRepository.countByUserIdAndRevokedFalse(userId);
        if (activeTokenCount >= maxRefreshTokensPerUser) {
            // Revoke oldest token
            List<RefreshToken> userTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
            if (!userTokens.isEmpty()) {
                RefreshToken oldestToken = userTokens.stream()
                    .min((t1, t2) -> t1.getCreatedDate().compareTo(t2.getCreatedDate()))
                    .orElse(null);
                if (oldestToken != null) {
                    revokeRefreshToken(oldestToken.getToken());
                }
            }
        }
        
        RefreshToken refreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .token(generateRefreshToken())
            .userId(userId)
            .expiryDate(LocalDateTime.now().plusSeconds(refreshTokenDurationMs / 1000))
            .createdDate(LocalDateTime.now())
            .lastUsedDate(LocalDateTime.now())
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .revoked(false)
            .build();
        
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Find refresh token by token string
     */
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Verify refresh token is valid and not expired
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isExpired() || token.isRevoked()) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired or revoked. Please make a new signin request");
        }
        
        // Update last used date
        token.setLastUsedDate(LocalDateTime.now());
        return refreshTokenRepository.save(token);
    }

    /**
     * Revoke a refresh token
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
        });
    }

    /**
     * Revoke all refresh tokens for a user (useful for logout all devices)
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        List<RefreshToken> userTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        userTokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(userTokens);
    }

    /**
     * Delete expired tokens for a specific user
     */
    @Transactional
    public void deleteExpiredTokensByUser(UUID userId) {
        List<RefreshToken> userTokens = refreshTokenRepository.findByUserId(userId);
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
        return refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
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
            LocalDateTime now = LocalDateTime.now();
            refreshTokenRepository.deleteByExpiryDateBefore(now);
            
            // Clean up revoked tokens (older than 7 days)
            List<RefreshToken> oldRevokedTokens = refreshTokenRepository
                .findByRevokedTrueAndCreatedDateBefore(now.minusDays(7));
            
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
