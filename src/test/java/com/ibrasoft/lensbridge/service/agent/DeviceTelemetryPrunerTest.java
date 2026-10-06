package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.repository.sql.DeviceTelemetryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTelemetryPrunerTest {

    private final DeviceTelemetryRepository repository = mock(DeviceTelemetryRepository.class);

    @Test
    void deletesRowsOlderThanTheRetentionWindow() {
        Instant now = Instant.parse("2026-10-06T03:30:00Z");
        when(repository.deleteOlderThan(any())).thenReturn(42);

        int removed = new DeviceTelemetryPruner(repository, 14).prune(now);

        assertThat(removed).isEqualTo(42);
        verify(repository).deleteOlderThan(now.minus(Duration.ofDays(14)));
    }

    @Test
    void retentionIsConfigurable() {
        Instant now = Instant.parse("2026-10-06T03:30:00Z");

        new DeviceTelemetryPruner(repository, 3).prune(now);

        verify(repository).deleteOlderThan(now.minus(Duration.ofDays(3)));
    }

    @Test
    void zeroOrNegativeRetentionDisablesPruning() {
        assertThat(new DeviceTelemetryPruner(repository, 0).prune(Instant.now())).isZero();
        assertThat(new DeviceTelemetryPruner(repository, -1).prune(Instant.now())).isZero();

        verify(repository, never()).deleteOlderThan(any());
    }

    /** A bulk @Modifying delete throws TransactionRequiredException outside a transaction. */
    @Test
    void theScheduledEntryPointRunsInASpringTransaction() throws Exception {
        var method = DeviceTelemetryPruner.class.getMethod("prune");

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(method.isAnnotationPresent(Scheduled.class)).isTrue();
        assertThat(method.getAnnotation(Scheduled.class).cron()).contains("musallahboard.telemetry.pruneCron");
    }
}
