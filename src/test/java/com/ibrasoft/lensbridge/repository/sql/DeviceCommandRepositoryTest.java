package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conditional status updates against a real database. Each must change a row only when the
 * command is still in an allowed state, and report that through the row count; the services'
 * race handling rests entirely on that.
 * <p>
 * No wrapping test transaction: {@code markDelivered} deliberately runs in its own one, and a
 * second connection cannot write while a test transaction holds SQLite's write lock.
 */
@SqliteDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceCommandRepositoryTest {

    private static final Set<DeviceCommandStatus> OPEN = Set.of(PENDING, DELIVERED, ACKED, RUNNING);

    @Autowired
    private DeviceCommandRepository repository;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Test
    void markDeliveredOnlyFromPending() {
        UUID pending = save(PENDING).getId();
        UUID acked = save(ACKED).getId();

        assertThat(repository.markDelivered(pending, now)).isEqualTo(1);
        assertThat(repository.markDelivered(acked, now)).isZero();
        assertThat(repository.markDelivered(pending, now)).isZero();

        DeviceCommand delivered = reload(pending);
        assertThat(delivered.getStatus()).isEqualTo(DELIVERED);
        assertThat(delivered.getDeliveredAt()).isNotNull();
        assertThat(reload(acked).getStatus()).isEqualTo(ACKED);
    }

    @Test
    void markAckedFromPendingOrDeliveredButNeverFromRunningOrTerminal() {
        UUID delivered = save(DELIVERED).getId();
        UUID running = save(RUNNING).getId();
        UUID done = save(SUCCEEDED).getId();

        assertThat(repository.markAcked(delivered, now)).isEqualTo(1);
        assertThat(repository.markAcked(running, now)).isZero();
        assertThat(repository.markAcked(done, now)).isZero();
        assertThat(repository.markAcked(delivered, now)).isZero(); // a repeated ack

        assertThat(reload(delivered).getStatus()).isEqualTo(ACKED);
        assertThat(reload(delivered).getAckedAt()).isNotNull();
        assertThat(reload(running).getStatus()).isEqualTo(RUNNING);
        assertThat(reload(done).getStatus()).isEqualTo(SUCCEEDED);
    }

    /** An ack that beats the DELIVERED write still has to leave a delivery time for the reaper. */
    @Test
    void markAckedStampsDeliveryTimeOnlyWhenMissing() {
        UUID outran = save(PENDING).getId();
        DeviceCommand alreadyDelivered = save(DELIVERED);
        Instant deliveredAt = now.minusSeconds(5);
        alreadyDelivered.setDeliveredAt(deliveredAt);
        repository.saveAndFlush(alreadyDelivered);

        repository.markAcked(outran, now);
        repository.markAcked(alreadyDelivered.getId(), now);

        assertThat(reload(outran).getDeliveredAt()).isEqualTo(now);
        assertThat(reload(alreadyDelivered.getId()).getDeliveredAt()).isEqualTo(deliveredAt);
    }

    @Test
    void markRunningFromAnyEarlierStateKeepingTheFirstStartTime() {
        UUID acked = save(ACKED).getId();
        UUID done = save(FAILED).getId();

        assertThat(repository.markRunning(acked, now)).isEqualTo(1);
        assertThat(repository.markRunning(acked, now.plusSeconds(9))).isZero();
        assertThat(repository.markRunning(done, now)).isZero();

        assertThat(reload(acked).getStatus()).isEqualTo(RUNNING);
        assertThat(reload(acked).getStartedAt()).isEqualTo(now);
        assertThat(reload(done).getStatus()).isEqualTo(FAILED);
    }

    @Test
    void finishWritesStatusOutputAndErrorTogetherFromAnOpenState() {
        UUID id = save(RUNNING).getId();

        int changed = repository.finish(id, SUCCEEDED, now, "{\"ok\":true}", null, OPEN);

        assertThat(changed).isEqualTo(1);
        DeviceCommand done = reload(id);
        assertThat(done.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(done.getFinishedAt()).isEqualTo(now);
        assertThat(done.getOutputJson()).isEqualTo("{\"ok\":true}");
        assertThat(done.getErrorMessage()).isNull();
    }

    /** The reaper-versus-result race: whichever finishes second must change nothing. */
    @Test
    void finishRefusesACommandThatAlreadyFinished() {
        UUID id = save(RUNNING).getId();
        assertThat(repository.finish(id, TIMEOUT, now, null, "no result", Set.of(DELIVERED, ACKED, RUNNING)))
                .isEqualTo(1);

        int second = repository.finish(id, SUCCEEDED, now.plusSeconds(1), "{\"late\":true}", null, OPEN);

        assertThat(second).isZero();
        DeviceCommand stored = reload(id);
        assertThat(stored.getStatus()).isEqualTo(TIMEOUT);
        assertThat(stored.getOutputJson()).isNull();
        assertThat(stored.getErrorMessage()).isEqualTo("no result");
    }

    @Test
    void finishHonoursTheStatesItIsAllowedToLeave() {
        UUID delivered = save(DELIVERED).getId();

        // Expiry is only for commands that were never delivered.
        assertThat(repository.finish(delivered, EXPIRED, now, null, "expired", Set.of(PENDING))).isZero();

        assertThat(reload(delivered).getStatus()).isEqualTo(DELIVERED);
    }

    private DeviceCommand save(DeviceCommandStatus status) {
        return repository.saveAndFlush(DeviceCommand.builder()
                .deviceId(UUID.randomUUID())
                .kind("chrome.reload")
                .payloadJson("{}")
                .issuedBy("admin")
                .deadlineMs(30_000)
                .status(status)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build());
    }

    private DeviceCommand reload(UUID id) {
        return repository.findById(id).orElseThrow();
    }
}
