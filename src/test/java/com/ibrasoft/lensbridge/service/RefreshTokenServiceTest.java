package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.exception.RefreshTokenException;
import com.ibrasoft.lensbridge.model.auth.RefreshToken;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.auth.RefreshTokenRepository;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        ReflectionTestUtils.setField(service, "pepper", "");
        userId = UUID.randomUUID();
        user = new User("A", "B", "a@b.ca", "p");
        user.setId(userId);
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Independent SHA-256 hex, so the test does not just re-run the implementation. */
    private static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Instant before = Instant.now();
        RefreshToken created = service.createRefreshToken(userId, "device", "1.2.3.4").token();

        long ttlSeconds = created.getExpiryDate().getEpochSecond() - before.getEpochSecond();
        assertThat(ttlSeconds).isCloseTo(604_800L, org.assertj.core.data.Offset.offset(5L));
        assertThat(created.isRevoked()).isFalse();
        assertThat(created.getTokenHash()).isNotBlank();
    }

    @Test
    void createRefreshTokenStoresHashNeverThePlaintext() {
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RefreshTokenService.IssuedRefreshToken issued = service.createRefreshToken(userId, "device", "1.2.3.4");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());

        assertThat(issued.plaintext()).isNotBlank();
        assertThat(saved.getValue().getTokenHash())
                .isNotEqualTo(issued.plaintext())
                .isEqualTo(sha256Hex(issued.plaintext()))
                .hasSize(64);
    }

    @Test
    void createRefreshTokenGeneratesADistinctPlaintextEachTime() {
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String first = service.createRefreshToken(userId, "device", "1.2.3.4").plaintext();
        String second = service.createRefreshToken(userId, "device", "1.2.3.4").plaintext();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void pepperIsMixedIntoTheStoredHash() {
        ReflectionTestUtils.setField(service, "pepper", "a-secret-pepper");
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RefreshTokenService.IssuedRefreshToken issued = service.createRefreshToken(userId, "device", "1.2.3.4");

        assertThat(issued.token().getTokenHash())
                .isNotEqualTo(sha256Hex(issued.plaintext()))
                .isEqualTo(sha256Hex("a-secret-pepper" + issued.plaintext()));
    }

    @Test
    void createRefreshTokenRevokesOldestWhenAtMaxLimit() {
        RefreshToken oldest = token(Instant.now().minusSeconds(1000), false);
        RefreshToken newer = token(Instant.now().minusSeconds(10), false);
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(5L);
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(userId)).thenReturn(List.of(newer, oldest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.createRefreshToken(userId, "device", "1.2.3.4");

        assertThat(oldest.isRevoked()).isTrue();
        assertThat(newer.isRevoked()).isFalse();
    }

    @Test
    void createRefreshTokenDoesNotRevokeWhenUnderLimit() {
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(4L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.createRefreshToken(userId, "device", "1.2.3.4");

        // findByUser_IdAndRevokedFalse only called for revocation path; under limit -> not called
        verify(refreshTokenRepository, never()).findByUser_IdAndRevokedFalse(userId);
    }

    @Test
    void createRefreshTokenThrowsWhenUserNotFound() {
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(refreshTokenRepository.countByUser_IdAndRevokedFalse(userId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRefreshToken(userId, "device", "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void verifyExpirationRejectsRevokedTokenAndDeletesIt() {
        RefreshToken revoked = token(Instant.now(), true);

        assertThatThrownBy(() -> service.verifyExpiration(revoked))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessageContaining("revoked");
        verify(refreshTokenRepository).delete(revoked);
    }

    @Test
    void verifyExpirationRejectsExpiredTokenAndDeletesIt() {
        RefreshToken expired = token(Instant.now(), false);
        expired.setExpiryDate(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.verifyExpiration(expired))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessageContaining("expired");
        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    void verifyExpirationUpdatesLastUsedDateForValidToken() {
        RefreshToken valid = token(Instant.now().minusSeconds(500), false);
        Instant oldLastUsed = valid.getLastUsedDate();

        RefreshToken result = service.verifyExpiration(valid);

        assertThat(result.getLastUsedDate()).isAfter(oldLastUsed);
        verify(refreshTokenRepository).save(valid);
    }

    @Test
    void revokeRefreshTokenHashesThePlaintextBeforeLookup() {
        RefreshToken t = token(Instant.now(), false);
        when(refreshTokenRepository.findByTokenHash(sha256Hex("the-plaintext"))).thenReturn(Optional.of(t));

        service.revokeRefreshToken("the-plaintext");

        assertThat(t.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(t);
        verify(refreshTokenRepository, never()).findByTokenHash("the-plaintext");
    }

    @Test
    void revokeRefreshTokenIsNoOpForUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(sha256Hex("nope"))).thenReturn(Optional.empty());

        service.revokeRefreshToken("nope");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeRefreshTokenIsNoOpForNullOrBlank() {
        service.revokeRefreshToken(null);
        service.revokeRefreshToken("   ");

        verify(refreshTokenRepository, never()).findByTokenHash(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void findByTokenLooksUpTheHashNotThePlaintext() {
        RefreshToken t = token(Instant.now(), false);
        when(refreshTokenRepository.findByTokenHash(sha256Hex("client-token"))).thenReturn(Optional.of(t));

        assertThat(service.findByToken("client-token")).contains(t);
        verify(refreshTokenRepository, never()).findByTokenHash("client-token");
    }

    @Test
    void findByTokenReturnsEmptyForNullOrBlankWithoutTouchingTheRepository() {
        assertThat(service.findByToken(null)).isEmpty();
        assertThat(service.findByToken("  ")).isEmpty();

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void revokeAllUserTokensRevokesEveryActiveToken() {
        RefreshToken a = token(Instant.now(), false);
        RefreshToken b = token(Instant.now(), false);
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(userId)).thenReturn(List.of(a, b));

        service.revokeAllUserTokens(userId);

        assertThat(a.isRevoked()).isTrue();
        assertThat(b.isRevoked()).isTrue();
        verify(refreshTokenRepository).saveAll(anyList());
    }

    @Test
    void deleteExpiredTokensByUserDeletesExpiredAndRevoked() {
        RefreshToken expired = token(Instant.now(), false);
        expired.setExpiryDate(Instant.now().minusSeconds(60));
        RefreshToken revoked = token(Instant.now(), true);
        RefreshToken active = token(Instant.now(), false);
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of(expired, revoked, active));

        service.deleteExpiredTokensByUser(userId);

        ArgumentCaptor<List<RefreshToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(refreshTokenRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(expired, revoked);
    }

    @Test
    void deleteExpiredTokensByUserSkipsDeleteWhenNothingExpired() {
        RefreshToken active = token(Instant.now(), false);
        when(refreshTokenRepository.findByUser_Id(userId)).thenReturn(List.of(active));

        service.deleteExpiredTokensByUser(userId);

        verify(refreshTokenRepository, never()).deleteAll(anyList());
    }

    @Test
    void cleanupExpiredTokensDeletesByExpiryAndOldRevoked() {
        RefreshToken oldRevoked = token(Instant.now().minusSeconds(99999), true);
        when(refreshTokenRepository.findByRevokedTrueAndCreatedDateBefore(any()))
                .thenReturn(List.of(oldRevoked));

        service.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiryDateBefore(any());
        verify(refreshTokenRepository).deleteAll(List.of(oldRevoked));
    }

    @Test
    void getActiveTokensForUserDelegatesToRepository() {
        RefreshToken a = token(Instant.now(), false);
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(userId)).thenReturn(List.of(a));

        assertThat(service.getActiveTokensForUser(userId)).containsExactly(a);
    }

    @Test
    void invalidateAllRefreshTokensForUserRevokesAndSaves() {
        RefreshToken a = token(Instant.now(), false);
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(userId)).thenReturn(List.of(a));

        service.invalidateAllRefreshTokensForUser(userId);

        assertThat(a.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(1)).saveAll(anyList());
    }
}
