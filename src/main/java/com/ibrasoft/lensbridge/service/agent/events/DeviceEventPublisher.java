package com.ibrasoft.lensbridge.service.agent.events;

import com.ibrasoft.lensbridge.dto.board.agent.CommandProgressFrame;
import com.ibrasoft.lensbridge.dto.board.agent.CommandResultFrame;
import com.ibrasoft.lensbridge.dto.board.agent.HeartbeatFrame;
import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thin wrapper over {@link SimpMessagingTemplate} that publishes structured events to
 * the dashboard's STOMP topics. All payloads are plain {@link Map}s so the dashboard
 * never needs to share schema with this module.
 * <p>
 * If the STOMP broker is not enabled (e.g. in tests that don't load {@code StompConfig}),
 * this component degrades to no-ops.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceEventPublisher {

    private static final String TOPIC_DEVICES = "/topic/devices";

    private final SimpMessagingTemplate messaging;

    public void deviceOnline(UUID deviceId) {
        send(TOPIC_DEVICES, Map.of("event", "online", "deviceId", deviceId, "at", Instant.now()));
        send(deviceTopic(deviceId), Map.of("event", "online", "deviceId", deviceId, "at", Instant.now()));
    }

    public void deviceOffline(UUID deviceId) {
        send(TOPIC_DEVICES, Map.of("event", "offline", "deviceId", deviceId, "at", Instant.now()));
        send(deviceTopic(deviceId), Map.of("event", "offline", "deviceId", deviceId, "at", Instant.now()));
    }

    public void heartbeat(UUID deviceId, HeartbeatFrame frame) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "heartbeat");
        body.put("deviceId", deviceId);
        body.put("at", Instant.now());
        body.put("telemetry", frame.getTelemetry());
        send(deviceTopic(deviceId), body);
    }

    public void commandIssued(DeviceCommand cmd) {
        publishCommandEvent(cmd, "issued", null);
    }

    public void commandDelivered(DeviceCommand cmd) {
        publishCommandEvent(cmd, "delivered", null);
    }

    public void commandAcked(DeviceCommand cmd) {
        publishCommandEvent(cmd, "acked", null);
    }

    public void commandProgress(DeviceCommand cmd, CommandProgressFrame frame) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("stage", frame.getStage());
        extra.put("message", frame.getMessage());
        extra.put("percent", frame.getPercent());
        publishCommandEvent(cmd, "progress", extra);
    }

    public void commandResult(DeviceCommand cmd, CommandResultFrame frame) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("status", frame.getStatus());
        extra.put("output", frame.getOutput());
        extra.put("durationMs", frame.getDurationMs());
        extra.put("errorMessage", frame.getErrorMessage());
        publishCommandEvent(cmd, "result", extra);
    }

    private void publishCommandEvent(DeviceCommand cmd, String event, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("commandId", cmd.getId());
        body.put("deviceId", cmd.getDeviceId());
        body.put("kind", cmd.getKind());
        body.put("status", cmd.getStatus());
        body.put("issuedAt", cmd.getIssuedAt());
        body.put("at", Instant.now());
        if (extra != null) body.putAll(extra);

        send(commandTopic(cmd.getDeviceId(), cmd.getId()), body);
        send(deviceCommandsTopic(cmd.getDeviceId()), body);
    }

    private void send(String destination, Object payload) {
        try {
            messaging.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("STOMP publish to {} failed: {}", destination, e.getMessage());
        }
    }

    private static String deviceTopic(UUID deviceId) {
        return TOPIC_DEVICES + "/" + deviceId;
    }

    private static String deviceCommandsTopic(UUID deviceId) {
        return TOPIC_DEVICES + "/" + deviceId + "/commands";
    }

    private static String commandTopic(UUID deviceId, UUID commandId) {
        return TOPIC_DEVICES + "/" + deviceId + "/commands/" + commandId;
    }
}
