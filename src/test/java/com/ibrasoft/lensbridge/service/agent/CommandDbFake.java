package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

/**
 * Backs a mocked {@link DeviceCommandRepository} with a small in-memory "table" so the
 * services can be tested against the conditional-update semantics without a database.
 * <p>
 * The point of keeping the table separate from what {@code findById} hands out is the race the
 * conditional updates exist for: {@code findById} returns a <em>copy</em>, so a test can read a
 * command, change the stored row underneath it (as a concurrent reaper or agent frame would)
 * and then let the service act on its now-stale copy. The update rules here mirror the JPQL in
 * the repository; {@code DeviceCommandRepositoryTest} runs that JPQL against SQLite.
 */
final class CommandDbFake {

    private static final Set<DeviceCommandStatus> ACKABLE =
            Set.of(DeviceCommandStatus.PENDING, DeviceCommandStatus.DELIVERED);
    private static final Set<DeviceCommandStatus> RUNNABLE =
            Set.of(DeviceCommandStatus.PENDING, DeviceCommandStatus.DELIVERED, DeviceCommandStatus.ACKED);

    private final Map<UUID, DeviceCommand> table = new HashMap<>();

    static CommandDbFake backing(DeviceCommandRepository repo) {
        CommandDbFake db = new CommandDbFake();
        db.install(repo);
        return db;
    }

    /** Inserts a row and returns the stored instance (what the "database" holds). */
    DeviceCommand insert(DeviceCommand cmd) {
        if (cmd.getId() == null) cmd.setId(UUID.randomUUID());
        table.put(cmd.getId(), cmd);
        return cmd;
    }

    /** The row as the database currently has it. */
    DeviceCommand row(UUID id) {
        return table.get(id);
    }

    private void install(DeviceCommandRepository repo) {
        lenient().when(repo.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(table.get(inv.<UUID>getArgument(0))).map(CommandDbFake::copy));
        lenient().when(repo.save(any())).thenAnswer(inv -> {
            DeviceCommand c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            if (c.getIssuedAt() == null) c.setIssuedAt(Instant.now());
            table.put(c.getId(), copy(c));
            return c;
        });
        lenient().when(repo.findByDeviceIdAndStatusOrderByIssuedAtAsc(any(), any())).thenAnswer(inv -> {
            UUID deviceId = inv.getArgument(0);
            DeviceCommandStatus status = inv.getArgument(1);
            List<DeviceCommand> out = new ArrayList<>();
            table.values().stream()
                    .filter(c -> deviceId.equals(c.getDeviceId()) && c.getStatus() == status)
                    .sorted((a, b) -> a.getIssuedAt().compareTo(b.getIssuedAt()))
                    .forEach(c -> out.add(copy(c)));
            return out;
        });
        lenient().when(repo.findByStatusAndExpiresAtBefore(any(), any())).thenAnswer(inv -> {
            DeviceCommandStatus status = inv.getArgument(0);
            Instant cutoff = inv.getArgument(1);
            return table.values().stream()
                    .filter(c -> c.getStatus() == status && c.getExpiresAt() != null && c.getExpiresAt().isBefore(cutoff))
                    .map(CommandDbFake::copy).toList();
        });
        lenient().when(repo.findByStatusInAndDeliveredAtBefore(anyCollection(), any())).thenAnswer(inv -> {
            Collection<DeviceCommandStatus> statuses = inv.getArgument(0);
            Instant cutoff = inv.getArgument(1);
            return table.values().stream()
                    .filter(c -> statuses.contains(c.getStatus())
                            && c.getDeliveredAt() != null && c.getDeliveredAt().isBefore(cutoff))
                    .map(CommandDbFake::copy).toList();
        });

        lenient().when(repo.markDelivered(any(), any())).thenAnswer(inv -> {
            DeviceCommand c = table.get(inv.<UUID>getArgument(0));
            if (c == null || c.getStatus() != DeviceCommandStatus.PENDING) return 0;
            c.setStatus(DeviceCommandStatus.DELIVERED);
            c.setDeliveredAt(inv.getArgument(1));
            return 1;
        });
        lenient().when(repo.markAcked(any(), any())).thenAnswer(inv -> {
            DeviceCommand c = table.get(inv.<UUID>getArgument(0));
            if (c == null || !ACKABLE.contains(c.getStatus())) return 0;
            Instant now = inv.getArgument(1);
            c.setStatus(DeviceCommandStatus.ACKED);
            c.setAckedAt(now);
            if (c.getDeliveredAt() == null) c.setDeliveredAt(now);
            return 1;
        });
        lenient().when(repo.markRunning(any(), any())).thenAnswer(inv -> {
            DeviceCommand c = table.get(inv.<UUID>getArgument(0));
            if (c == null || !RUNNABLE.contains(c.getStatus())) return 0;
            Instant now = inv.getArgument(1);
            c.setStatus(DeviceCommandStatus.RUNNING);
            if (c.getStartedAt() == null) c.setStartedAt(now);
            if (c.getDeliveredAt() == null) c.setDeliveredAt(now);
            return 1;
        });
        lenient().when(repo.finish(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            DeviceCommand c = table.get(inv.<UUID>getArgument(0));
            Collection<DeviceCommandStatus> from = inv.getArgument(5);
            if (c == null || !from.contains(c.getStatus())) return 0;
            c.setStatus(inv.getArgument(1));
            c.setFinishedAt(inv.getArgument(2));
            c.setOutputJson(inv.getArgument(3));
            c.setErrorMessage(inv.getArgument(4));
            return 1;
        });
    }

    static DeviceCommand copy(DeviceCommand c) {
        return DeviceCommand.builder()
                .id(c.getId()).deviceId(c.getDeviceId()).kind(c.getKind()).payloadJson(c.getPayloadJson())
                .issuedBy(c.getIssuedBy()).deadlineMs(c.getDeadlineMs()).expiresAt(c.getExpiresAt())
                .status(c.getStatus()).issuedAt(c.getIssuedAt()).deliveredAt(c.getDeliveredAt())
                .ackedAt(c.getAckedAt()).startedAt(c.getStartedAt()).finishedAt(c.getFinishedAt())
                .outputJson(c.getOutputJson()).errorMessage(c.getErrorMessage())
                .build();
    }
}
