package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.AuthTokenProperties;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.model.auth.VerificationToken.TokenType;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import com.ibrasoft.lensbridge.repository.auth.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationTokenServiceTest {

    @Mock
    private VerificationTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthTokenProperties authTokenProperties;

    @InjectMocks
    private VerificationTokenService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("A", "B", "1", "a@b.ca", "p");
        lenient().when(tokenRepository.save(any(VerificationToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generateEmailVerificationTokenPersistsHashedTokenWithTtl() {
        when(authTokenProperties.getVerificationExpirationMs()).thenReturn(3_600_000L);

        Instant before = Instant.now();
        String plaintext = service.generateEmailVerificationToken(user);

        assertThat(plaintext).hasSize(64); // 32 random bytes -> 64 hex chars
        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        VerificationToken saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(TokenType.EMAIL_VERIFICATION);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTokenHash()).isNotEqualTo(plaintext);
        long ttl = saved.getExpiresAt().toEpochMilli() - before.toEpochMilli();
        assertThat(ttl).isBetween(3_600_000L - 5000, 3_600_000L + 5000);
    }

    @Test
    void generatePasswordResetTokenUsesPasswordResetTtlAndType() {
        when(authTokenProperties.getPasswordResetExpirationMs()).thenReturn(900_000L);

        Instant before = Instant.now();
        service.generatePasswordResetToken(user);

        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        VerificationToken saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(TokenType.PASSWORD_RESET);
        long ttl = saved.getExpiresAt().toEpochMilli() - before.toEpochMilli();
        assertThat(ttl).isBetween(900_000L - 5000, 900_000L + 5000);
    }

    @Test
    void tokensForSameInputAreUniqueAcrossCalls() {
        when(authTokenProperties.getVerificationExpirationMs()).thenReturn(1000L);

        String first = service.generateEmailVerificationToken(user);
        String second = service.generateEmailVerificationToken(user);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void consumeEmailVerificationMarksTokenUsedAndVerifiesUser() {
        when(authTokenProperties.getVerificationExpirationMs()).thenReturn(3_600_000L);
        String plaintext = service.generateEmailVerificationToken(user);
        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        VerificationToken stored = captor.getValue();
        when(tokenRepository.findValidToken(eq(stored.getTokenHash()), eq(TokenType.EMAIL_VERIFICATION), any()))
                .thenReturn(Optional.of(stored));

        User result = service.consumeEmailVerification(plaintext);

        assertThat(stored.getUsedAt()).isNotNull();
        assertThat(result.isVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void consumeEmailVerificationRejectsInvalidToken() {
        when(tokenRepository.findValidToken(any(), eq(TokenType.EMAIL_VERIFICATION), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeEmailVerification("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
        verify(userRepository, never()).save(any());
    }

    @Test
    void consumePasswordResetMarksUsedAndReturnsToken() {
        VerificationToken token = VerificationToken.builder()
                .tokenHash("h").user(user).type(TokenType.PASSWORD_RESET)
                .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(tokenRepository.findValidToken(any(), eq(TokenType.PASSWORD_RESET), any()))
                .thenReturn(Optional.of(token));

        VerificationToken result = service.consumePasswordReset("plain");

        assertThat(result.getUsedAt()).isNotNull();
        assertThat(result).isSameAs(token);
    }

    @Test
    void consumePasswordResetRejectsInvalidToken() {
        when(tokenRepository.findValidToken(any(), eq(TokenType.PASSWORD_RESET), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumePasswordReset("bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired password reset");
    }

    @Test
    void isValidResetTokenReturnsTrueWhenTokenPresent() {
        VerificationToken token = VerificationToken.builder().type(TokenType.PASSWORD_RESET).build();
        when(tokenRepository.findValidToken(any(), eq(TokenType.PASSWORD_RESET), any()))
                .thenReturn(Optional.of(token));

        assertThat(service.isValidResetToken("plain")).isTrue();
    }

    @Test
    void isValidResetTokenReturnsFalseWhenAbsent() {
        when(tokenRepository.findValidToken(any(), eq(TokenType.PASSWORD_RESET), any()))
                .thenReturn(Optional.empty());

        assertThat(service.isValidResetToken("plain")).isFalse();
    }

    @Test
    void consumeEmailVerificationHashesPlaintextWithSha256() {
        // Sanity-check the hash contract: lookup must use the SHA-256 hex of the plaintext,
        // not the plaintext itself.
        when(authTokenProperties.getVerificationExpirationMs()).thenReturn(1000L);
        String plaintext = service.generateEmailVerificationToken(user);
        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(captor.capture());

        String expectedHash = sha256Hex(plaintext);
        assertThat(captor.getValue().getTokenHash()).isEqualTo(expectedHash);
    }

    private static String sha256Hex(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(input.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
