package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.exception.RefreshTokenException;
import com.ibrasoft.lensbridge.model.auth.RefreshToken;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.auth.RefreshTokenRepository;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import com.ibrasoft.lensbridge.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenService service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTokenDurationMs", 604_800_000L);
        ReflectionTestUtils.setField(service, "maxRefreshTokensPerUser", 5);
        userId = UUID.randomUUID();
        user = new User("A", "B", "1", "a@b.ca", "p");
        user.setId(userId);
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private RefreshToken token(Instant created, boolean revoked) {
        return RefreshToken.builder()
                .tokenHash(UUID.randomUUID().toString())
                .user(user)
                .createdDate(created)
                .expiryDate(Instant.now().plusSeconds(3600))
                .lastUsedDate(created)
                .revoked(revoked)
                .build();
    }

    @Test
    void createRefreshTokenSetsExpiryFromDurationProperty() {
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Instant before = Instant.now();
        RefreshToken created = service.createRefreshToken(userId).entity();

        long ttlSeconds = created.getExpiryDate().getEpochSecond() - before.getEpochSecond();
        assertThat(ttlSeconds).isCloseTo(604_800L, org.assertj.core.data.Offset.offset(5L));
        assertThat(created.isRevoked()).isFalse();
        assertThat(created.getTokenHash()).isNotBlank();
    }

    @Test
    void createRefreshTokenStoresOnlyTheHashAndReturnsTheRawToken() {
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RefreshTokenService.IssuedToken issued = service.createRefreshToken(userId);

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.entity().getTokenHash())
                .isEqualTo(TokenHasher.sha256Hex(issued.rawToken()))
                .isNotEqualTo(issued.rawToken());
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).doesNotContain(issued.rawToken());
    }

    @Test
    void createRefreshTokenRevokesOldestWhenAtMaxLimit() {
        RefreshToken oldest = token(Instant.now().minusSeconds(1000), false);
        RefreshToken newer = token(Instant.now().minusSeconds(10), false);
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(5L);
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(userId)).thenReturn(List.of(newer, oldest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.createRefreshToken(userId);

        verify(refreshTokenRepository).deleteByHash(oldest.getTokenHash());
        verify(refreshTokenRepository, never()).deleteByHash(newer.getTokenHash());
        verify(refreshTokenRepository, never()).revokeIfActive(any());
    }

    @Test
    void createRefreshTokenDoesNotRevokeWhenUnderLimit() {
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(4L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.createRefreshToken(userId);

        // findByUser_IdAndRevokedFalse only called for revocation path; under limit -> not called
        verify(refreshTokenRepository, never()).findByUser_IdAndRevokedFalse(userId);
    }

    @Test
    void createRefreshTokenThrowsWhenUserNotFound() {
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRefreshToken(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void rotateRevokesPresentedTokenAndIssuesReplacement() {
        String raw = "raw-token";
        String hash = TokenHasher.sha256Hex(raw);
        RefreshToken stored = token(Instant.now().minusSeconds(60), false);
        stored.setTokenHash(hash);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfActive(hash)).thenReturn(1);
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(1L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RefreshTokenService.IssuedToken rotated = service.rotate(raw);

        assertThat(rotated.rawToken()).isNotEqualTo(raw);
        assertThat(rotated.entity().getUserId()).isEqualTo(userId);
        verify(refreshTokenRepository).revokeIfActive(hash);
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any());
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessageContaining("not found");
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any());
    }

    @Test
    void rotateOfRevokedTokenRevokesEveryTokenOfTheUserAndKeepsTheRevokedRow() {
        String raw = "replayed";
        RefreshToken revoked = token(Instant.now(), true);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(raw))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessageContaining("revoked")
                .extracting(e -> ((RefreshTokenException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(refreshTokenRepository).revokeAllActiveForUser(userId);
        // The revoked row must stay so a further replay is still recognised as reuse.
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateThatLosesTheRaceFailsAsReplayAndRevokesEverything() {
        String raw = "contended";
        String hash = TokenHasher.sha256Hex(raw);
        RefreshToken stored = token(Instant.now(), false);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfActive(hash)).thenReturn(0);

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessageContaining("revoked");
        verify(refreshTokenRepository).revokeAllActiveForUser(userId);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRejectsExpiredTokenAndDeletesIt() {
        String raw = "old";
        RefreshToken expired = token(Instant.now(), false);
        expired.setExpiryDate(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(raw))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessageContaining("expired");
        verify(refreshTokenRepository).delete(expired);
        verify(refreshTokenRepository, never()).revokeIfActive(any());
    }

    @Test
    void revokeRefreshTokenDeletesByHashOfTheRawToken() {
        service.revokeRefreshToken("raw");

        verify(refreshTokenRepository).deleteByHash(TokenHasher.sha256Hex("raw"));
    }

    @Test
    void revokeAllUserTokensDeletesEveryToken() {
        service.revokeAllUserTokens(userId);

        verify(refreshTokenRepository).deleteAllForUser(userId);
    }

    @Test
    void deleteExpiredTokensByUserOnlyDeletesExpiredSoReuseDetectionKeepsRevokedRows() {
        service.deleteExpiredTokensByUser(userId);

        verify(refreshTokenRepository).deleteExpiredForUser(eq(userId), any(Instant.class));
    }

    @Test
    void cleanupExpiredTokensBulkDeletesExpiredAndOldRevoked() {
        service.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteExpired(any(Instant.class));
        verify(refreshTokenRepository).deleteRevokedCreatedBefore(any(Instant.class));
    }
}
