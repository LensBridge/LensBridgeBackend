package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.request.CreateDeviceRequest;
import com.ibrasoft.lensbridge.dto.board.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.dto.board.request.IssueEnrollmentTokenRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateDeviceRequest;
import com.ibrasoft.lensbridge.dto.board.response.CommandIssuedResponse;
import com.ibrasoft.lensbridge.dto.board.response.CommandView;
import com.ibrasoft.lensbridge.dto.board.response.DeviceSummary;
import com.ibrasoft.lensbridge.dto.board.response.IssueEnrollmentTokenResponse;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.commands.CommandKind;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.ContentChangeNotifier;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService.Issued;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundle;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import com.ibrasoft.lensbridge.service.board.offline.SetupFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
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
    private final OfflineBundleService offlineBundleService;
    private final BoardService boardService;
    private final ContentChangeNotifier contentChanges;
    private final SetupFileService setupFileService;

    @Operation(operationId = "listDevices", summary = "List every board, connected or not")
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

    @Operation(operationId = "createDevice",
            summary = "Add a board",
            description = "Creates the board before it is ever switched on, with no device key. Its config, "
                    + "content packages and setup file work at once; it gets a key only if it later claims "
                    + "online. Requires board:device:enroll.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Board created"),
            @ApiResponse(responseCode = "400", description = "Missing name or audience, or an unusable location",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_ENROLL + "')")
    public ResponseEntity<DeviceSummary> createDevice(@Valid @RequestBody CreateDeviceRequest request,
                                                      HttpServletRequest httpRequest) {
        Device created = boardService.createDevice(request);
        // There is no CREATE_DEVICE action (the column has a check constraint); this is the
        // closest, and the detail says what happened.
        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_BOARD_CONFIG,
                "Device", created.getId(), httpRequest.getRemoteAddr(), "created board");
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceSummary.of(created));
    }

    /**
     * Same reasons as {@link #offlineBundle}: built first, written straight to the response.
     * A GET that changes state (the sequence, and the claim secret when online) because it is
     * a download link; downloading again simply issues a newer file.
     */
    @Operation(operationId = "downloadSetupFile",
            summary = "Download a board's setup file",
            description = "A signed .mbu provision package (provision.json: deviceId, displayName, sequence, "
                    + "issuedAt and, with online=true, backendUrl and a claimSecret). Plugged into a fresh board "
                    + "on a USB stick, it makes the board this one. Every download has a newer sequence; an "
                    + "online one also replaces the claim secret, so earlier online files can no longer claim "
                    + "the board. Requires board:device:enroll.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The setup file",
                    headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                            description = "attachment; filename=\"musallahboard-setup-<first 8 chars of deviceId>.mbu\"",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = OfflineBundleService.MBU_CONTENT_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "409", description = "Device is revoked",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "503", description = "No content signing key, or (online) no backend URL, "
                    + "is configured on the server",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @GetMapping("/{deviceId}/setup-file")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_ENROLL + "')")
    public void setupFile(
            @PathVariable UUID deviceId,
            @Parameter(description = "Include the backend URL and a new claim secret, for a board that will "
                    + "be online. Default: an offline-only board.")
            @RequestParam(defaultValue = "false") boolean online,
            HttpServletRequest httpRequest,
            HttpServletResponse response) throws IOException {
        OfflineBundle file = setupFileService.build(deviceId, online);
        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.ISSUE_ENROLLMENT_TOKEN,
                "Device", deviceId, httpRequest.getRemoteAddr(),
                online ? "setup file (online, claim secret rotated)" : "setup file (offline)");
        writePackage(file, response);
    }

    @Operation(operationId = "updateDevice", summary = "Rename a board or change its audience")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device updated"),
            @ApiResponse(responseCode = "400", description = "Blank or over-long displayName",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PatchMapping("/{deviceId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONFIG_WRITE + "')")
    public ResponseEntity<DeviceSummary> updateDevice(@PathVariable UUID deviceId,
                                                      @Valid @RequestBody UpdateDeviceRequest request,
                                                      HttpServletRequest httpRequest) {
        Device updated = boardService.updateDevice(deviceId, request);
        // The audience decides which posters, events and socials the board shows.
        contentChanges.deviceChanged(deviceId, "device");
        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_BOARD_CONFIG,
                "Device", deviceId, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(DeviceSummary.of(updated));
    }

    @Operation(operationId = "issueEnrollmentToken",
            summary = "Mint a one-time token that enrolls a new device over SSH (legacy)",
            description = "The token creates a new device when used. To enroll a board already added in the "
                    + "console, use POST /api/admin/board/devices/{deviceId}/enrollment-tokens.")
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

    @Operation(operationId = "issueDeviceEnrollmentToken",
            summary = "Mint a one-time token that enrolls this board over SSH (maintainers)",
            description = "For musallahboard-agent enroll --token on the board. The token binds the board's key to "
                    + "this device, replacing any key it had (for example after an SD card swap). Boards set up "
                    + "with an online setup file claim by themselves and need no token.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Token issued; the plaintext is returned exactly once"),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "409", description = "Device is revoked",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/{deviceId}/enrollment-tokens")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_ENROLL + "')")
    public ResponseEntity<IssueEnrollmentTokenResponse> issueDeviceEnrollmentToken(
            @PathVariable UUID deviceId,
            @Parameter(description = "Lifetime of the token in minutes (1-1440, default 30)")
            @RequestParam(required = false) Integer expiresInMinutes,
            HttpServletRequest httpRequest) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, new MessageResponse("Device not found")));
        if (device.getRevokedAt() != null) {
            throw new ApiResponseException(HttpStatus.CONFLICT, new MessageResponse("Device is revoked"));
        }
        String adminEmail = getCurrentUserEmail();
        Issued issued = enrollmentTokenService.issueFor(device, expiresInMinutes, adminEmail);
        log.info("Admin {} issued enrollment token {} for device {}", adminEmail, issued.token().getId(), deviceId);
        auditService.logAuditEvent(adminEmail, AuditAction.ISSUE_ENROLLMENT_TOKEN,
                "Device", deviceId, httpRequest.getRemoteAddr(), "token " + issued.token().getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(IssueEnrollmentTokenResponse.builder()
                .tokenId(issued.token().getId())
                .token(issued.plaintext())
                .expiresAt(issued.token().getExpiresAt())
                .build());
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
     * privilege; see {@link com.ibrasoft.lensbridge.model.board.commands.CommandRisk}.
     */
    @Operation(operationId = "issueDeviceCommand",
            summary = "Queue a command for a device",
            description = "Returns as soon as the command is queued; delivery and execution are reported "
                    + "asynchronously over the agent websocket. The permission required depends on the "
                    + "command kind: board:command:benign, :disruptive, or :inspect. ttlSeconds is capped per "
                    + "kind (10 minutes for kiosk.restart, system.reboot and update.install_now; 15 for "
                    + "chrome.screenshot and logs.tail; an hour for chrome.reload and config.refresh), "
                    + "deadlineMs at 15 minutes. Only logs.tail takes a payload, {\"lines\": 1..500}; for "
                    + "the others it must be absent, null or {}.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Command accepted and queued for delivery"),
            @ApiResponse(responseCode = "400", description = "Unknown kind, a payload the kind does not take, "
                    + "or a deadline or time-to-live over its limit",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
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

    @Operation(operationId = "listDeviceCommands", summary = "Fetch the 50 most recent commands for a device")
    @GetMapping("/{deviceId}/commands")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_READ + "')")
    public ResponseEntity<List<CommandView>> recentCommands(@PathVariable UUID deviceId) {
        List<CommandView> commands = commandRepository
                .findTop50ByDeviceIdOrderByIssuedAtDesc(deviceId)
                .stream()
                .map(c -> CommandView.of(c, objectMapper))
                .toList();
        return ResponseEntity.ok(commands);
    }

    /**
     * The signed content package ({@code .mbu}) an admin carries to a board, with every media
     * file included. It is the same package an online board syncs for itself, just with no
     * {@code haveMedia} to leave anything out.
     * <p>
     * The package is built completely before the first byte goes out, so every failure (bad
     * {@code days}, unknown or revoked device, no signing key, an unfetchable poster image) is
     * still a normal JSON error response rather than a truncated zip.
     * <p>
     * Written straight to the servlet response on the request thread. A
     * {@code StreamingResponseBody} would finish on an ASYNC re-dispatch, where
     * {@code AuthTokenFilter} (a {@code OncePerRequestFilter}) does not run, so the
     * authorization filter would reject the dispatch and append a 401 body to the zip.
     * No {@code produces} on the mapping either: it would stop Spring rendering the JSON
     * error bodies, since the only producible type would be the package.
     */
    @Operation(operationId = "downloadOfflineBundle",
            summary = "Download a signed content package for a board",
            description = "A signed .mbu content package (format version 2) holding one fully assembled "
                    + "payload per day, starting today in the device's timezone, plus every poster image those "
                    + "payloads reference. Taken to the board by USB stick, laptop or phone. Requires board:device:read.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The package",
                    headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                            description = "attachment; filename=\"musallahboard-content-<first 8 chars of deviceId>-<firstDay>.mbu\"",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = OfflineBundleService.MBU_CONTENT_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "days outside 1-92",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "409", description = "Device is revoked",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "502", description = "A poster image could not be fetched; the message names the poster",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "503", description = "No content signing key is configured on the server",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @GetMapping("/{deviceId}/offline-bundle")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_DEVICE_READ + "')")
    public void offlineBundle(
            @PathVariable UUID deviceId,
            @Parameter(description = "Number of days in the bundle, starting today in the device's timezone",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1",
                            maximum = "" + OfflineBundleService.MAX_DAYS,
                            defaultValue = "" + OfflineBundleService.DEFAULT_DAYS))
            @RequestParam(defaultValue = "" + OfflineBundleService.DEFAULT_DAYS) int days,
            HttpServletResponse response) throws IOException {
        writePackage(offlineBundleService.build(deviceId, days), response);
    }

    private static void writePackage(OfflineBundle bundle, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(OfflineBundleService.MBU_CONTENT_TYPE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(bundle.filename()).build().toString());
        bundle.writeTo(response.getOutputStream());
        response.flushBuffer();
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }

    /** Audit detail for a command: the kind and its risk class, so the log distinguishes a reload from a screenshot. */
    private static String describe(String kind, UUID commandId) {
        String risk = CommandKind.from(kind)
                .map(k -> k.getRisk().name())
                .orElse("UNKNOWN");
        return "kind=" + kind + " risk=" + risk + " commandId=" + commandId;
    }
}
