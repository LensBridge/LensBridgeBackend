package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.dto.board.request.IssueEnrollmentTokenRequest;
import com.ibrasoft.lensbridge.dto.board.response.CommandIssuedResponse;
import com.ibrasoft.lensbridge.dto.board.response.CommandView;
import com.ibrasoft.lensbridge.dto.board.response.DeviceSummary;
import com.ibrasoft.lensbridge.dto.board.response.IssueEnrollmentTokenResponse;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.model.minbar.board.commands.CommandKind;
import com.ibrasoft.lensbridge.model.minbar.board.commands.CommandRisk;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.AdminAuditService;
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
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * Fleet administration. Authorization is per-endpoint by permission: reading device status
 * is something you hand out freely, minting an enrollment token is not, and issuing a
 * command depends on which command it is. See {@code docs/PERMISSIONS.md}.
 */
@RestController
@RequestMapping("/api/admin/board/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceAdminController {

    private final EnrollmentTokenService enrollmentTokenService;
    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository commandRepository;
    private final CommandDispatcher commandDispatcher;
    private final ObjectMapper objectMapper;
    private final AgentSessionRegistry sessionRegistry;
    private final AdminAuditService auditService;

    @Operation(operationId = "listDevices", summary = "List every enrolled board device")
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_READ + "')")
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
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_READ + "')")
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
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_ENROLL + "')")
    public ResponseEntity<IssueEnrollmentTokenResponse> issueEnrollmentToken(
            @Valid @RequestBody IssueEnrollmentTokenRequest request,
            HttpServletRequest httpRequest) {
        String adminEmail = getCurrentUserEmail();
        Issued issued = enrollmentTokenService.issue(
                request.getDisplayName(),
                request.getAudience(),
                request.getExpiresInMinutes(),
            adminEmail
        );
        log.info("Admin {} issued enrollment token {} ({})",
            adminEmail, issued.token().getId(), request.getDisplayName());

        auditService.logAuditEvent(adminEmail, AuditAction.ISSUE_ENROLLMENT_TOKEN,
                "Device", issued.token().getId(), httpRequest.getRemoteAddr());

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
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_REVOKE + "')")
    public ResponseEntity<DeviceSummary> revoke(@PathVariable UUID deviceId,
                                                HttpServletRequest httpRequest) {
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

            auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.REVOKE_DEVICE,
                    "Device", deviceId, httpRequest.getRemoteAddr());
        }
        return ResponseEntity.ok(DeviceSummary.of(d));
    }

    /**
     * The required permission depends on the command's blast radius, which is only knowable
     * once the body is bound - hence the authorizer bean rather than a {@code hasAuthority}
     * expression. {@code chrome.reload} and {@code chrome.screenshot} are not the same
     * privilege; see {@link CommandRisk}.
     */
    @Operation(operationId = "issueDeviceCommand",
            summary = "Queue a command for a device",
            description = "Returns as soon as the command is queued; delivery and execution are reported "
                    + "asynchronously over the agent websocket. The permission required depends on the "
                    + "command kind: board:command:benign, :disruptive, or :inspect.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Command accepted and queued for delivery"),
            @ApiResponse(responseCode = "403", description = "Caller lacks the permission for this command kind",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/{deviceId}/commands")
    @PreAuthorize("@commandAuthorizer.canIssue(authentication, #request)")
    public ResponseEntity<CommandIssuedResponse> issueCommand(@PathVariable UUID deviceId,
                                          @Valid @RequestBody IssueCommandRequest request,
                                          HttpServletRequest httpRequest) {
        CommandIssuedResponse response = commandDispatcher.issue(deviceId, getCurrentUserEmail(), request);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.ISSUE_DEVICE_COMMAND,
                "Device", deviceId, httpRequest.getRemoteAddr(),
                describe(request.kind(), response.commandId()));

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Command metadata is fleet status, so {@code BOARD_DEVICE_READ} gates the endpoint. The
     * {@code output} field is not: for {@code chrome.screenshot} it is an image of a physical
     * display and for {@code logs.tail} it is agent internals, which
     * {@link com.ibrasoft.lensbridge.config.stomp.StompJwtChannelInterceptor} already gates behind
     * {@link Permission#BOARD_TELEMETRY_SUBSCRIBE} on the live topic. Without the same carve-out
     * here, history replay would hand a plain fleet reader every screenshot ever captured of a
     * board. Redact rather than refuse: a lower-privilege operator should still see that a
     * screenshot was taken.
     */
    @Operation(operationId = "listDeviceCommands",
            summary = "Fetch the 50 most recent commands for a device",
            description = "Command output (a screenshot image, a log tail) is only included for callers "
                    + "holding board:telemetry:subscribe, matching the live /topic/devices/** channel. "
                    + "Other callers get the same metadata with output omitted and outputRedacted=true.")
    @GetMapping("/{deviceId}/commands")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_READ + "')")
    public ResponseEntity<List<CommandView>> recentCommands(@PathVariable UUID deviceId) {
        boolean includeOutput = currentUserHasAuthority(Permission.Authority.BOARD_TELEMETRY_SUBSCRIBE);
        List<CommandView> commands = commandRepository
                .findTop50ByDeviceIdOrderByIssuedAtDesc(deviceId)
                .stream()
                .map(c -> CommandView.of(c, objectMapper, includeOutput))
                .toList();
        return ResponseEntity.ok(commands);
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }

    /** Fails closed: no authentication, or an unauthenticated one, holds nothing. */
    private static boolean currentUserHasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    /** Audit detail for a command: the kind and its risk class, so the log distinguishes a reload from a screenshot. */
    private static String describe(String kind, UUID commandId) {
        String risk = CommandKind.from(kind)
                .map(k -> k.getRisk().name())
                .orElse("UNKNOWN");
        return "kind=" + kind + " risk=" + risk + " commandId=" + commandId;
    }
}
