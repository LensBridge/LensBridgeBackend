package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import com.ibrasoft.lensbridge.repository.sql.EnrollmentTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentTokenServiceTest {

    @Mock
    private EnrollmentTokenRepository repository;

    @InjectMocks
    private EnrollmentTokenService service;

    @BeforeEach
    void echoSave() {
        lenient().when(repository.save(any(EnrollmentToken.class))).thenAnswer(inv -> {
            EnrollmentToken t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
    }

    @Test
    void issueProducesPlaintextAndPersistsHash() {
        var issued = service.issue("Brothers Display", Audience.BROTHERS, 30, "admin@example.com");

        assertNotNull(issued.plaintext());
        assertEquals(24, issued.plaintext().length(), "expected 24-char base64 token");
        assertNotNull(issued.token().getTokenHash());
        assertEquals(32, issued.token().getTokenHash().length, "SHA-256 hash should be 32 bytes");
        assertEquals(Audience.BROTHERS, issued.token().getAudience());
        assertEquals("admin@example.com", issued.token().getCreatedBy());
        assertTrue(issued.token().getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void issueClampsTtl() {
        var issued = service.issue("X", Audience.BOTH, 999_999, "admin@x");
        long minutes = (issued.token().getExpiresAt().toEpochMilli() - issued.token().getCreatedAt().toEpochMilli()) / 60_000;
        assertTrue(minutes <= 60 * 24, "TTL should clamp to 24h max but got " + minutes);
    }

    @Test
    void consumeHappyPath() {
        var issued = service.issue("X", Audience.BOTH, 30, "admin@x");
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(issued.token()));
        when(repository.markConsumed(eq(issued.token().getId()), any(Instant.class))).thenReturn(1);

        Optional<EnrollmentToken> consumed = service.consume(issued.plaintext());

        assertTrue(consumed.isPresent());
        assertNotNull(consumed.get().getConsumedAt());
    }

    /**
     * The single-use guarantee lives in the conditional UPDATE: a row count other than 1 means
     * someone else consumed the token (or it expired) between our read and our write, and this
     * caller must lose even though its read saw an unconsumed token.
     */
    @Test
    void consumeLosesWhenTheConditionalUpdateChangesNoRow() {
        var issued = service.issue("X", Audience.BOTH, 30, "admin@x");
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(issued.token()));
        when(repository.markConsumed(any(), any())).thenReturn(0);

        assertTrue(service.consume(issued.plaintext()).isEmpty());
    }

    @Test
    void twoRacersOnOneTokenCannotBothWin() {
        var issued = service.issue("X", Audience.BOTH, 30, "admin@x");
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(issued.token()));
        // Both read an unconsumed row; the database lets exactly one UPDATE through.
        when(repository.markConsumed(any(), any())).thenReturn(1, 0);

        boolean first = service.consume(issued.plaintext()).isPresent();
        boolean second = service.consume(issued.plaintext()).isPresent();

        assertTrue(first);
        assertFalse(second);
    }

    @Test
    void consumeRejectsUnknown() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertTrue(service.consume("nonsense").isEmpty());
        verify(repository, never()).markConsumed(any(), any());
    }

    @Test
    void consumeRejectsBlank() {
        assertTrue(service.consume(null).isEmpty());
        assertTrue(service.consume("   ").isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void recordConsumerPointsTheTokenAtItsDevice() {
        UUID tokenId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        service.recordConsumer(tokenId, deviceId);

        verify(repository).recordConsumer(tokenId, deviceId);
    }
}
