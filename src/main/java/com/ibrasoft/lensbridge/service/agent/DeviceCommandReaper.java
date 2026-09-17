package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Drives commands to a terminal state when the agent never will.
 * <p>
 * Two failure modes are covered. A command can sit PENDING because the device never came
 * back — that becomes {@link DeviceCommandStatus#EXPIRED}. Or the agent can take delivery
 * and then die mid-execution, leaving the row DELIVERED/ACKED/RUNNING forever — that
 * becomes {@link DeviceCommandStatus#TIMEOUT}. Without this, both statuses were declared
 * but never assigned, and the admin UI showed commands as permanently in flight.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCommandReaper {

    /** Non-terminal states that imply the agent has the command and owes a result. */
    private static final Set<DeviceCommandStatus> IN_FLIGHT = EnumSet.of(
            DeviceCommandStatus.DELIVERED,
            DeviceCommandStatus.ACKED,
            DeviceCommandStatus.RUNNING);

    /** Slack on top of a command's own deadline, covering agent→backend result latency. */
    private static final Duration RESULT_GRACE = Duration.ofSeconds(15);

    private final DeviceCommandRepository commandRepository;
    private final CommandDispatcher dispatcher;
    private final DeviceEventPublisher events;

    @Scheduled(fixedDelayString = "${musallahboard.command.reaperIntervalMs:60000}")
    @Transactional
    public void reap() {
        Instant now = Instant.now();
        expireUndelivered(now);
        timeoutInFlight(now);
    }

    private void expireUndelivered(Instant now) {
        List<DeviceCommand> stale =
                commandRepository.findByStatusAndExpiresAtBefore(DeviceCommandStatus.PENDING, now);
        for (DeviceCommand cmd : stale) {
            dispatcher.expire(cmd, now);
        }
    }

    private void timeoutInFlight(Instant now) {
        Instant cutoff = now.minus(RESULT_GRACE);
        List<DeviceCommand> candidates =
                commandRepository.findByStatusInAndDeliveredAtBefore(IN_FLIGHT, cutoff);

        for (DeviceCommand cmd : candidates) {
            Instant deadline = cmd.getDeliveredAt()
                    .plusMillis(cmd.getDeadlineMs() == null ? 0 : cmd.getDeadlineMs())
                    .plus(RESULT_GRACE);
            if (!deadline.isBefore(now)) continue;

            DeviceCommandStatus stuckAt = cmd.getStatus();
            cmd.setStatus(DeviceCommandStatus.TIMEOUT);
            cmd.setFinishedAt(now);
            cmd.setErrorMessage("No result from agent within " + cmd.getDeadlineMs() + "ms of delivery");
            commandRepository.save(cmd);
            events.commandTerminated(cmd);
            log.warn("Command {} ({}) for device {} timed out while {}",
                    cmd.getId(), cmd.getKind(), cmd.getDeviceId(), stuckAt);
        }
    }
}
