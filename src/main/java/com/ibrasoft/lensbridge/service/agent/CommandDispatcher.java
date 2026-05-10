package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.ibrasoft.lensbridge.dto.board.agent.CommandAckFrame;
import com.ibrasoft.lensbridge.dto.board.agent.CommandProgressFrame;
import com.ibrasoft.lensbridge.dto.board.agent.CommandResultFrame;
import com.ibrasoft.lensbridge.dto.board.agent.OutgoingAgentFrame;
import com.ibrasoft.lensbridge.dto.board.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.dto.board.response.CommandIssuedResponse;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import com.ibrasoft.lensbridge.model.board.commands.CommandPayload;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges admin REST → agent WebSocket and the reverse path (ack/progress/result frames).
 * <p>
 * Responsibilities:
 * <ol>
 *   <li><b>Issue:</b> persist a {@link DeviceCommand}, push to a live session if one exists,
 *       otherwise leave PENDING for {@link #flushPending(UUID, AgentSession)}.</li>
 *   <li><b>Flush:</b> on session auth, deliver all PENDING commands oldest-first.</li>
 *   <li><b>State machine:</b> consume ack/progress/result frames and advance the
 *       {@link DeviceCommandStatus} state machine.</li>
 * </ol>
 * Idempotency: the agent dedupes by {@code commandId}, so re-sending a command after a
 * reconnect is safe — the agent re-emits a cached result.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommandDispatcher {

    private static final int DEFAULT_DEADLINE_MS = 30_000;

    /** Discriminator strings registered on {@link CommandPayload}. Updated when adding a command kind. */
    private static final Set<String> KNOWN_KINDS = Set.of(
            "chrome.reload",
            "chrome.screenshot",
            "kiosk.restart",
            "system.reboot",
            "config.refresh",
            "logs.tail"
    );

    private final DeviceCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;
    private final AgentSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final DeviceEventPublisher events;

    @Transactional
    public CommandIssuedResponse issue(UUID deviceId, String issuedBy, IssueCommandRequest request) {
        if (!KNOWN_KINDS.contains(request.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown command kind: " + request.kind());
        }
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        if (device.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Device is revoked");
        }

        JsonNode payload = request.payload() == null ? NullNode.getInstance() : request.payload();
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
        }

        int deadline = request.deadlineMs() == null ? DEFAULT_DEADLINE_MS : request.deadlineMs();

        DeviceCommand cmd = commandRepository.save(DeviceCommand.builder()
                .deviceId(deviceId)
                .kind(request.kind())
                .payloadJson(payloadJson)
                .issuedBy(issuedBy)
                .deadlineMs(deadline)
                .status(DeviceCommandStatus.PENDING)
                .build());

        events.commandIssued(cmd);

        registry.get(deviceId).ifPresent(session -> deliverTo(cmd, session));

        log.info("Issued command {} ({}) for device {} by {} — initial status {}",
                cmd.getId(), cmd.getKind(), deviceId, issuedBy, cmd.getStatus());

        return new CommandIssuedResponse(
                cmd.getId(), cmd.getDeviceId(), cmd.getKind(), cmd.getStatus(), cmd.getIssuedAt());
    }

    /** Delivers all PENDING commands for the device to the freshly-authenticated session. */
    @Transactional
    public void flushPending(UUID deviceId, AgentSession session) {
        List<DeviceCommand> pending = commandRepository
                .findByDeviceIdAndStatusOrderByIssuedAtAsc(deviceId, DeviceCommandStatus.PENDING);
        if (pending.isEmpty()) return;
        log.info("Flushing {} pending command(s) to device {}", pending.size(), deviceId);
        for (DeviceCommand cmd : pending) {
            deliverTo(cmd, session);
        }
    }

    private void deliverTo(DeviceCommand cmd, AgentSession session) {
        try {
            JsonNode payload = cmd.getPayloadJson() == null
                    ? NullNode.getInstance()
                    : objectMapper.readTree(cmd.getPayloadJson());

            OutgoingAgentFrame frame = session.populateOutgoing(OutgoingAgentFrame.builder()
                    .type("command")
                    .commandId(cmd.getId())
                    .kind(cmd.getKind())
                    .issuedBy(cmd.getIssuedBy())
                    .deadlineMs(cmd.getDeadlineMs())
                    .payload(payload));
            if (!session.send(frame)) {
                log.warn("Leaving command {} PENDING because delivery to device {} did not complete",
                        cmd.getId(), cmd.getDeviceId());
                return;
            }

            cmd.setStatus(DeviceCommandStatus.DELIVERED);
            cmd.setDeliveredAt(Instant.now());
            commandRepository.save(cmd);
            events.commandDelivered(cmd);
        } catch (Exception e) {
            log.error("Failed to deliver command {} to device {}: {}",
                    cmd.getId(), cmd.getDeviceId(), e.getMessage(), e);
        }
    }

    @Transactional
    public void onAck(CommandAckFrame frame, AgentSession session) {
        loadOwned(frame.getCommandId(), session.getDeviceId()).ifPresent(cmd -> {
            if (cmd.getStatus().isTerminal()) return;
            if (cmd.getAckedAt() != null) return; // idempotent re-ack
            cmd.setStatus(DeviceCommandStatus.ACKED);
            cmd.setAckedAt(Instant.now());
            commandRepository.save(cmd);
            events.commandAcked(cmd);
        });
    }

    @Transactional
    public void onProgress(CommandProgressFrame frame, AgentSession session) {
        loadOwned(frame.getCommandId(), session.getDeviceId()).ifPresent(cmd -> {
            if (cmd.getStatus().isTerminal()) return;
            if (cmd.getStatus() != DeviceCommandStatus.RUNNING) {
                cmd.setStatus(DeviceCommandStatus.RUNNING);
                if (cmd.getStartedAt() == null) cmd.setStartedAt(Instant.now());
                commandRepository.save(cmd);
            }
            events.commandProgress(cmd, frame);
        });
    }

    @Transactional
    public void onResult(CommandResultFrame frame, AgentSession session) {
        loadOwned(frame.getCommandId(), session.getDeviceId()).ifPresent(cmd -> {
            if (cmd.getStatus().isTerminal()) return;

            DeviceCommandStatus terminal = mapResultStatus(frame.getStatus());
            cmd.setStatus(terminal);
            cmd.setFinishedAt(Instant.now());
            if (frame.getOutput() != null && !frame.getOutput().isNull()) {
                cmd.setOutputJson(frame.getOutput().toString());
            }
            if (frame.getErrorMessage() != null) {
                cmd.setErrorMessage(frame.getErrorMessage());
            }
            commandRepository.save(cmd);
            events.commandResult(cmd, frame);

            log.info("Command {} for device {} terminated as {} (durationMs={})",
                    cmd.getId(), cmd.getDeviceId(), terminal, frame.getDurationMs());
        });
    }

    private Optional<DeviceCommand> loadOwned(UUID commandId, UUID expectedDeviceId) {
        if (commandId == null || expectedDeviceId == null) return Optional.empty();
        return commandRepository.findById(commandId).filter(cmd -> {
            if (!expectedDeviceId.equals(cmd.getDeviceId())) {
                log.warn("Frame references command {} owned by device {}, but session is for {}",
                        commandId, cmd.getDeviceId(), expectedDeviceId);
                return false;
            }
            return true;
        });
    }

    private static DeviceCommandStatus mapResultStatus(String status) {
        if (status == null) return DeviceCommandStatus.FAILED;
        return switch (status.toLowerCase()) {
            case "ok", "success", "succeeded" -> DeviceCommandStatus.SUCCEEDED;
            case "timeout" -> DeviceCommandStatus.TIMEOUT;
            case "rejected" -> DeviceCommandStatus.REJECTED;
            default -> DeviceCommandStatus.FAILED;
        };
    }
}
