package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.repository.sql.DeviceTelemetryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes telemetry samples past their retention period.
 * <p>
 * Every connected device adds a row per heartbeat (2,880 a day each) and nothing else ever
 * removes them, so without this the table only grows. Telemetry is for spotting a board in
 * trouble now, not for history; the latest values also live on the device row itself.
 * <p>
 * Runs once a day at 03:30 server time ({@code musallahboard.telemetry.pruneCron}); the cutoff
 * is {@code musallahboard.telemetry.retentionDays} (default 14) back from the run. A retention
 * of zero or less disables pruning.
 */
@Service
@Slf4j
public class DeviceTelemetryPruner {

    private final DeviceTelemetryRepository telemetryRepository;
    private final int retentionDays;

    public DeviceTelemetryPruner(
            DeviceTelemetryRepository telemetryRepository,
            @Value("${musallahboard.telemetry.retentionDays:14}") int retentionDays) {
        this.telemetryRepository = telemetryRepository;
        this.retentionDays = retentionDays;
    }

    /** One bulk DELETE, so a single transaction covers it (a modifying query requires one). */
    @Scheduled(cron = "${musallahboard.telemetry.pruneCron:0 30 3 * * *}")
    @Transactional
    public int prune() {
        return prune(Instant.now());
    }

    int prune(Instant now) {
        if (retentionDays <= 0) {
            log.debug("Telemetry pruning disabled (retentionDays={})", retentionDays);
            return 0;
        }
        Instant cutoff = now.minus(Duration.ofDays(retentionDays));
        int removed = telemetryRepository.deleteOlderThan(cutoff);
        if (removed > 0) {
            log.info("Pruned {} telemetry row(s) older than {} ({} days)", removed, cutoff, retentionDays);
        }
        return removed;
    }
}
