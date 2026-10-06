package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.AuthTokenProperties;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.model.auth.VerificationToken.TokenType;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import com.ibrasoft.lensbridge.repository.auth.VerificationTokenRepository;
import com.ibrasoft.lensbridge.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationTokenService {

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuthTokenProperties authTokenProperties;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public String generateEmailVerificationToken(User user) {
        return createToken(user, TokenType.EMAIL_VERIFICATION,
                Duration.ofMillis(authTokenProperties.getVerificationExpirationMs()));
    }

    @Transactional
    public String generatePasswordResetToken(User user) {
        return createToken(user, TokenType.PASSWORD_RESET,
                Duration.ofMillis(authTokenProperties.getPasswordResetExpirationMs()));
    }

    @Transactional
    public User consumeEmailVerification(String plaintextToken) {
        String hash = resolveHash(plaintextToken);
        VerificationToken token = tokenRepository.findValidToken(hash, TokenType.EMAIL_VERIFICATION, Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
        invalidateOutstanding(token.getUser(), TokenType.EMAIL_VERIFICATION);

        User user = token.getUser();
        user.setVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public VerificationToken consumePasswordReset(String plaintextToken) {
        String hash = resolveHash(plaintextToken);
        VerificationToken token = tokenRepository.findValidToken(hash, TokenType.PASSWORD_RESET, Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        token.setUsedAt(Instant.now());
        VerificationToken consumed = tokenRepository.save(token);
        // A reset requested twice must not leave the older email usable after the newer link
        // has been spent.
        invalidateOutstanding(token.getUser(), TokenType.PASSWORD_RESET);
        return consumed;
    }

    public boolean isValidResetToken(String plaintextToken) {
        String hash = resolveHash(plaintextToken);
        return tokenRepository.findValidToken(hash, TokenType.PASSWORD_RESET, Instant.now()).isPresent();
    }

    /**
     * Daily purge so spent and lapsed tokens do not accumulate. Neither can be redeemed
     * (findValidToken filters both), so deleting them changes no behaviour.
     */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void purgeExpiredAndUsedTokens() {
        try {
            int deleted = tokenRepository.deleteExpiredOrUsed(Instant.now());
            log.info("Purged {} expired or used verification tokens", deleted);
        } catch (Exception e) {
            log.error("Failed to purge verification tokens", e);
        }
    }

    /**
     * Marks the user's other unused tokens of this type as used. Only the most recently issued
     * link should work: an older email sitting in the inbox is exactly the one an attacker with
     * mailbox history would try.
     */
    private void invalidateOutstanding(User user, TokenType type) {
        tokenRepository.markOutstandingUsed(user, type, Instant.now());
    }

    private String createToken(User user, TokenType type, Duration ttl) {
        invalidateOutstanding(user, type);

        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String plaintext = HexFormat.of().formatHex(bytes);
        String hash = resolveHash(plaintext);

        VerificationToken token = VerificationToken.builder()
                .tokenHash(hash)
                .user(user)
                .type(type)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(ttl))
                .build();

        tokenRepository.save(token);
        return plaintext;
    }

    private String resolveHash(String plaintext) {
        return TokenHasher.sha256Hex(plaintext);
    }
}
