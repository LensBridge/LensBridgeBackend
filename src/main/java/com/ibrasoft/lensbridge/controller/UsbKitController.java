package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import com.ibrasoft.lensbridge.service.board.offline.SetupFileService;
import com.ibrasoft.lensbridge.service.board.offline.UsbKitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * The USB kit downloads (contract C4): everything a volunteer copies onto a stick, for one board
 * or for every board. Both endpoints live here, not in {@link DeviceAdminController}, so the kit
 * is one file to read; the one-board URL sits under that controller's {@code /devices} prefix.
 * <p>
 * Reading devices is enough for a content-only kit. Setup files need board:device:enroll, since
 * an online one carries a claim secret.
 */
@RestController
@RequestMapping("/api/admin/board")
@RequiredArgsConstructor
public class UsbKitController {

    /** Lists what the kit goes without (the latest app or agent when GitHub could not be reached). */
    public static final String OMITTED_HEADER = "X-Usb-Kit-Omitted";

    private static final String KIT_ACCESS = "hasAuthority('" + Permission.Authority.BOARD_DEVICE_READ + "')"
            + " and (!#includeSetup or hasAuthority('" + Permission.Authority.BOARD_DEVICE_ENROLL + "'))";

    private final UsbKitService usbKitService;
    private final DeviceRepository deviceRepository;
    private final AdminAuditService auditService;
    private final SetupFileService setupFileService;

    @Operation(operationId = "downloadUsbKit", summary = "Download a USB kit for one board",
            description = "A zip holding one folder, MusallahBoard/, with content-<id8>.mbu, setup-<id8>.mbu "
                    + "(includeSetup=true; online=true adds the backend URL and a new claim secret), and the latest "
                    + "board app and arm64 agent packages from their GitHub release channels. When a channel cannot be "
                    + "reached the kit goes without that package and the " + OMITTED_HEADER + " header says why. "
                    + "Requires board:device:read, and board:device:enroll with includeSetup.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The kit",
                    headers = {
                            @Header(name = HttpHeaders.CONTENT_DISPOSITION, schema = @Schema(type = "string"),
                                    description = "attachment; filename=\"musallahboard-usb-kit-<id8>-<date>.zip\""),
                            @Header(name = OMITTED_HEADER, schema = @Schema(type = "string"),
                                    description = "Present when the kit is missing the app or agent package: what, and why")},
                    content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "days outside 1-92",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "409", description = "Device is revoked",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "502", description = "A poster image could not be fetched",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "503", description = "No content signing key (or, online, no backend URL) is configured",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @GetMapping("/devices/{deviceId}/usb-kit")
    @PreAuthorize(KIT_ACCESS)
    public void deviceKit(@PathVariable UUID deviceId,
                          @Parameter(description = "Days of content, starting today in each board's timezone",
                                  schema = @Schema(type = "integer", format = "int32", minimum = "1",
                                          maximum = "" + OfflineBundleService.MAX_DAYS,
                                          defaultValue = "" + OfflineBundleService.DEFAULT_DAYS))
                          @RequestParam(defaultValue = "" + OfflineBundleService.DEFAULT_DAYS) int days,
                          @Parameter(description = "Include the board's setup file")
                          @RequestParam(defaultValue = "false") boolean includeSetup,
                          @Parameter(description = "Make the setup file an online one (backend URL and a new claim secret). Default: online whenever the server has a backend URL for boards")
                          @RequestParam(name = "online", required = false) Boolean onlineParam,
                          HttpServletRequest request, HttpServletResponse response) throws IOException {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, new MessageResponse("Device not found")));
        String filename = "musallahboard-usb-kit-" + deviceId.toString().substring(0, 8) + "-" + today() + ".zip";
        boolean online = onlineParam != null ? onlineParam : setupFileService.onlineByDefault();
        send(usbKitService.build(List.of(device), filename, days, includeSetup, online), includeSetup, online,
                deviceId, request, response);
    }

    @Operation(operationId = "downloadAllBoardsUsbKit", summary = "Download a USB kit for every board",
            description = "As the one-board kit, with the files of every board that is not revoked. One stick then "
                    + "updates any of them: each board keeps only its own files.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The kit",
                    headers = {
                            @Header(name = HttpHeaders.CONTENT_DISPOSITION, schema = @Schema(type = "string"),
                                    description = "attachment; filename=\"musallahboard-usb-kit-all-<date>.zip\""),
                            @Header(name = OMITTED_HEADER, schema = @Schema(type = "string"),
                                    description = "Present when the kit is missing the app or agent package: what, and why")},
                    content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "days outside 1-92",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "404", description = "There are no boards",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "502", description = "A poster image could not be fetched",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "503", description = "No content signing key (or, online, no backend URL) is configured",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @GetMapping("/usb-kit")
    @PreAuthorize(KIT_ACCESS)
    public void allBoardsKit(@Parameter(description = "Days of content, starting today in each board's timezone",
                                     schema = @Schema(type = "integer", format = "int32", minimum = "1",
                                             maximum = "" + OfflineBundleService.MAX_DAYS,
                                             defaultValue = "" + OfflineBundleService.DEFAULT_DAYS))
                             @RequestParam(defaultValue = "" + OfflineBundleService.DEFAULT_DAYS) int days,
                             @Parameter(description = "Include every board's setup file")
                             @RequestParam(defaultValue = "false") boolean includeSetup,
                             @Parameter(description = "Make the setup files online ones (backend URL and new claim secrets). Default: online whenever the server has a backend URL for boards")
                             @RequestParam(name = "online", required = false) Boolean onlineParam,
                             HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<Device> boards = deviceRepository.findByRevokedAtIsNullOrderByDisplayNameAsc();
        String filename = "musallahboard-usb-kit-all-" + today() + ".zip";
        boolean online = onlineParam != null ? onlineParam : setupFileService.onlineByDefault();
        send(usbKitService.build(boards, filename, days, includeSetup, online), includeSetup, online,
                null, request, response);
    }

    /** Built completely first (see UsbKitService), then copied to the response on this thread. */
    private void send(UsbKitService.Kit kit, boolean includeSetup, boolean online, UUID deviceId,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (kit) {
            if (includeSetup) {
                auditService.logAuditEvent(currentUser(), AuditAction.ISSUE_ENROLLMENT_TOKEN, "Device", deviceId,
                        request.getRemoteAddr(), online ? "USB kit with setup files (online, claim secrets rotated)"
                                : "USB kit with setup files (offline)");
            }
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(kit.filename()).build().toString());
            if (!kit.omitted().isEmpty()) {
                // Header values must be plain ASCII on one line.
                response.setHeader(OMITTED_HEADER, String.join("; ", kit.omitted())
                        .replaceAll("[^\\x20-\\x7E]", "?"));
            }
            response.setContentLengthLong(Files.size(kit.file()));
            Files.copy(kit.file(), response.getOutputStream());
            response.flushBuffer();
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
