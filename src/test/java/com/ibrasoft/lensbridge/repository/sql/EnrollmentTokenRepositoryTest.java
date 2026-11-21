package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SqliteDataJpaTest
class EnrollmentTokenRepositoryTest {

    @Autowired
    private EnrollmentTokenRepository repository;
    @Autowired
    private EntityManager em;

    @Test
    void markConsumedSucceedsOnceThenRefusesEveryoneElse() {
        EnrollmentToken token = saveToken(Instant.now().plus(10, ChronoUnit.MINUTES));
        Instant now = Instant.now();

        assertThat(repository.markConsumed(token.getId(), now)).isEqualTo(1);
        assertThat(repository.markConsumed(token.getId(), now.plusMillis(1))).isZero();

        EnrollmentToken reloaded = repository.findById(token.getId()).orElseThrow();
        assertThat(reloaded.getConsumedAt()).isNotNull();
    }

    @Test
    void markConsumedRefusesAnExpiredToken() {
        EnrollmentToken token = saveToken(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(repository.markConsumed(token.getId(), Instant.now())).isZero();

        assertThat(repository.findById(token.getId()).orElseThrow().getConsumedAt()).isNull();
    }

    @Test
    void markConsumedDoesNotLetAStaleLoadedCopyOverwriteTheUpdate() {
        EnrollmentToken token = saveToken(Instant.now().plus(10, ChronoUnit.MINUTES));

        // The copy in the persistence context still says "unconsumed" when the UPDATE runs.
        repository.markConsumed(token.getId(), Instant.now());
        repository.recordConsumer(token.getId(), UUID.randomUUID());
        em.flush();
        em.clear();

        EnrollmentToken reloaded = repository.findById(token.getId()).orElseThrow();
        assertThat(reloaded.getConsumedAt()).isNotNull();
        assertThat(reloaded.getConsumedByDeviceId()).isNotNull();
    }

    private EnrollmentToken saveToken(Instant expiresAt) {
        return repository.saveAndFlush(EnrollmentToken.builder()
                .tokenHash(UUID.randomUUID().toString().getBytes())
                .displayName("Lobby")
                .audience(Audience.BOTH)
                .createdBy("admin")
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build());
    }
}
