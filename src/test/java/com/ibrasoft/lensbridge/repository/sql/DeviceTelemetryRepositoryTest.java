package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.DeviceTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SqliteDataJpaTest
class DeviceTelemetryRepositoryTest {

    @Autowired
    private DeviceTelemetryRepository repository;

    @Test
    void deleteOlderThanRemovesOnlyRowsBeforeTheCutoff() {
        Instant now = Instant.now();
        UUID device = UUID.randomUUID();
        sample(device, now.minus(30, ChronoUnit.DAYS));
        sample(device, now.minus(15, ChronoUnit.DAYS));
        DeviceTelemetry recent = sample(device, now.minus(1, ChronoUnit.DAYS));
        DeviceTelemetry fresh = sample(device, now);

        int removed = repository.deleteOlderThan(now.minus(14, ChronoUnit.DAYS));

        assertThat(removed).isEqualTo(2);
        assertThat(repository.findAll()).extracting(DeviceTelemetry::getId)
                .containsExactlyInAnyOrder(recent.getId(), fresh.getId());
    }

    @Test
    void deleteOlderThanOnAnEmptyTableRemovesNothing() {
        assertThat(repository.deleteOlderThan(Instant.now())).isZero();
    }

    private DeviceTelemetry sample(UUID deviceId, Instant recordedAt) {
        return repository.saveAndFlush(DeviceTelemetry.builder()
                .deviceId(deviceId)
                .recordedAt(recordedAt)
                .uptimeSec(100L)
                .build());
    }
}
