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

        UUID deviceId = UUID.randomUUID();
        Optional<EnrollmentToken> consumed = service.consume(issued.plaintext(), deviceId);

        assertTrue(consumed.isPresent());
        assertNotNull(consumed.get().getConsumedAt());
        assertEquals(deviceId, consumed.get().getConsumedByDeviceId());
    }

    @Test
    void consumeRejectsAlreadyUsed() {
        var issued = service.issue("X", Audience.BOTH, 30, "admin@x");
        issued.token().setConsumedAt(Instant.now().minusSeconds(60));
        issued.token().setConsumedByDeviceId(UUID.randomUUID());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(issued.token()));

        Optional<EnrollmentToken> consumed = service.consume(issued.plaintext(), UUID.randomUUID());

        assertTrue(consumed.isEmpty());
        verify(repository, never()).save(argThat(t ->
                t != issued.token() // we don't write a fresh row
        ));
    }

    @Test
    void consumeRejectsExpired() {
        var issued = service.issue("X", Audience.BOTH, 30, "admin@x");
        issued.token().setExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(issued.token()));

        Optional<EnrollmentToken> consumed = service.consume(issued.plaintext(), UUID.randomUUID());

        assertTrue(consumed.isEmpty());
    }

    @Test
    void consumeRejectsUnknown() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertTrue(service.consume("nonsense", UUID.randomUUID()).isEmpty());
    }

    @Test
    void consumeRejectsBlank() {
        assertTrue(service.consume(null, UUID.randomUUID()).isEmpty());
        assertTrue(service.consume("   ", UUID.randomUUID()).isEmpty());
        verifyNoInteractions(repository);
    }
}
