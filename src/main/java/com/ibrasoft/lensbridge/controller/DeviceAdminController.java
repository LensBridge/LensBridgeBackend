package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.dto.request.IssueEnrollmentTokenRequest;
import com.ibrasoft.lensbridge.dto.response.CommandIssuedResponse;
import com.ibrasoft.lensbridge.dto.response.CommandView;
import com.ibrasoft.lensbridge.dto.response.DeviceSummary;
import com.ibrasoft.lensbridge.dto.response.IssueEnrollmentTokenResponse;
import com.ibrasoft.lensbridge.dto.response.MessageResponse;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService.Issued;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasRole('" + Role.ROOT + "')")
@Slf4j
public class DeviceAdminController {

    private final EnrollmentTokenService enrollmentTokenService;
    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository commandRepository;
    private final CommandDispatcher commandDispatcher;
    private final ObjectMapper objectMapper;
    private final AgentSessionRegistry sessionRegistry;

    @GetMapping
    public ResponseEntity<List<DeviceSummary>> list() {
        return ResponseEntity.ok(
                deviceRepository.findAll().stream().map(DeviceSummary::of).toList()
        );
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<?> get(@PathVariable UUID deviceId) {
        Optional<Device> found = deviceRepository.findById(deviceId);
        return found.<ResponseEntity<?>>map(d -> ResponseEntity.ok(DeviceSummary.of(d)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new MessageResponse("Device not found")));
    }

    @PostMapping("/enrollment-tokens")
    public ResponseEntity<IssueEnrollmentTokenResponse> issueEnrollmentToken(
            @Valid @RequestBody IssueEnrollmentTokenRequest request) {
        UserDetailsImpl admin = currentUser();
        Issued issued = enrollmentTokenService.issue(
                request.getDisplayName(),
                request.getAudience(),
                request.getExpiresInMinutes(),
                admin.getEmail()
        );
        log.info("Admin {} issued enrollment token {} ({})",
                admin.getEmail(), issued.token().getId(), request.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                IssueEnrollmentTokenResponse.builder()
                        .tokenId(issued.token().getId())
                        .token(issued.plaintext())
                        .expiresAt(issued.token().getExpiresAt())
                        .build()
        );
    }

    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<?> revoke(@PathVariable UUID deviceId) {
        Optional<Device> found = deviceRepository.findById(deviceId);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MessageResponse("Device not found"));
        }
        Device d = found.get();
        if (d.getRevokedAt() == null) {
            d.setRevokedAt(Instant.now());
            deviceRepository.save(d);
            sessionRegistry.closeIfPresent(deviceId, CloseStatus.POLICY_VIOLATION.withReason("device_revoked"));
            log.warn("Device {} revoked by {}", deviceId, currentUser().getEmail());
        }
        return ResponseEntity.ok(DeviceSummary.of(d));
    }

    @PostMapping("/{deviceId}/commands")
    public ResponseEntity<?> issueCommand(@PathVariable UUID deviceId,
                                          @Valid @RequestBody IssueCommandRequest request) {
        UserDetailsImpl admin = currentUser();
        CommandIssuedResponse response = commandDispatcher.issue(deviceId, admin.getEmail(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{deviceId}/commands")
    public ResponseEntity<List<CommandView>> recentCommands(@PathVariable UUID deviceId) {
        List<CommandView> commands = commandRepository
                .findTop50ByDeviceIdOrderByIssuedAtDesc(deviceId)
                .stream()
                .map(c -> CommandView.of(c, objectMapper))
                .toList();
        return ResponseEntity.ok(commands);
    }

    private UserDetailsImpl currentUser() {
        return (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
