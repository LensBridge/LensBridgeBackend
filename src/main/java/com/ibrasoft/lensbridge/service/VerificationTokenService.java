package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.AuthTokenProperties;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.model.auth.VerificationToken.TokenType;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import com.ibrasoft.lensbridge.repository.auth.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuthTokenProperties authTokenProperties;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateEmailVerificationToken(User user) {
        return createToken(user, TokenType.EMAIL_VERIFICATION,
                Duration.ofMillis(authTokenProperties.getVerificationExpirationMs()));
    }

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
        return tokenRepository.save(token);
    }

    public boolean isValidResetToken(String plaintextToken) {
        String hash = resolveHash(plaintextToken);
        return tokenRepository.findValidToken(hash, TokenType.PASSWORD_RESET, Instant.now()).isPresent();
    }

    private String createToken(User user, TokenType type, Duration ttl) {
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plaintext.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
