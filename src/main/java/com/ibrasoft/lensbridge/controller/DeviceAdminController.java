package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.dto.board.request.IssueEnrollmentTokenRequest;
import com.ibrasoft.lensbridge.dto.board.response.CommandIssuedResponse;
import com.ibrasoft.lensbridge.dto.board.response.CommandView;
import com.ibrasoft.lensbridge.dto.board.response.DeviceSummary;
import com.ibrasoft.lensbridge.dto.board.response.IssueEnrollmentTokenResponse;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService.Issued;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.CloseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/board/devices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.Authority.ROOT + "')")
@Slf4j
public class DeviceAdminController {

    private final EnrollmentTokenService enrollmentTokenService;
    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository commandRepository;
    private final CommandDispatcher commandDispatcher;
    private final ObjectMapper objectMapper;
    private final AgentSessionRegistry sessionRegistry;

    @Operation(operationId = "listDevices", summary = "List every enrolled board device")
    @GetMapping
    public ResponseEntity<List<DeviceSummary>> list() {
        return ResponseEntity.ok(
                deviceRepository.findAll().stream().map(DeviceSummary::of).toList()
        );
    }

    @Operation(operationId = "getDevice", summary = "Fetch a single board device")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device found"),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceSummary> get(@PathVariable UUID deviceId) {
        return deviceRepository.findById(deviceId)
                .map(d -> ResponseEntity.ok(DeviceSummary.of(d)))
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND,
                        new MessageResponse("Device not found")));
    }

    @Operation(operationId = "issueEnrollmentToken",
            summary = "Mint a one-time token a new device can exchange for an identity")
    @ApiResponse(responseCode = "201", description = "Token issued; the plaintext is returned exactly once")
    @PostMapping("/enrollment-tokens")
    public ResponseEntity<IssueEnrollmentTokenResponse> issueEnrollmentToken(
            @Valid @RequestBody IssueEnrollmentTokenRequest request) {
        String adminEmail = getCurrentUserEmail();
        Issued issued = enrollmentTokenService.issue(
                request.getDisplayName(),
                request.getAudience(),
                request.getExpiresInMinutes(),
            adminEmail
        );
        log.info("Admin {} issued enrollment token {} ({})",
            adminEmail, issued.token().getId(), request.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                IssueEnrollmentTokenResponse.builder()
                        .tokenId(issued.token().getId())
                        .token(issued.plaintext())
                        .expiresAt(issued.token().getExpiresAt())
                        .build()
        );
    }

    @Operation(operationId = "revokeDevice",
            summary = "Revoke a device's enrollment",
            description = "Closes any live agent session. Idempotent: re-revoking an already revoked device is a no-op.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device revoked"),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<DeviceSummary> revoke(@PathVariable UUID deviceId) {
        Optional<Device> found = deviceRepository.findById(deviceId);
        if (found.isEmpty()) {
            throw new ApiResponseException(HttpStatus.NOT_FOUND,
                    new MessageResponse("Device not found"));
        }
        Device d = found.get();
        if (d.getRevokedAt() == null) {
            d.setRevokedAt(Instant.now());
            deviceRepository.save(d);
            sessionRegistry.closeIfPresent(deviceId, CloseStatus.POLICY_VIOLATION.withReason("device_revoked"));
            log.warn("Device {} revoked by {}", deviceId, getCurrentUserEmail());
        }
        return ResponseEntity.ok(DeviceSummary.of(d));
    }

    @Operation(operationId = "issueDeviceCommand",
            summary = "Queue a command for a device",
            description = "Returns as soon as the command is queued; delivery and execution are reported "
                    + "asynchronously over the agent websocket.")
    @ApiResponse(responseCode = "202", description = "Command accepted and queued for delivery")
    @PostMapping("/{deviceId}/commands")
    public ResponseEntity<CommandIssuedResponse> issueCommand(@PathVariable UUID deviceId,
                                          @Valid @RequestBody IssueCommandRequest request) {
        CommandIssuedResponse response = commandDispatcher.issue(deviceId, getCurrentUserEmail(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(operationId = "listDeviceCommands", summary = "Fetch the 50 most recent commands for a device")
    @GetMapping("/{deviceId}/commands")
    public ResponseEntity<List<CommandView>> recentCommands(@PathVariable UUID deviceId) {
        List<CommandView> commands = commandRepository
                .findTop50ByDeviceIdOrderByIssuedAtDesc(deviceId)
                .stream()
                .map(c -> CommandView.of(c, objectMapper))
                .toList();
        return ResponseEntity.ok(commands);
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
