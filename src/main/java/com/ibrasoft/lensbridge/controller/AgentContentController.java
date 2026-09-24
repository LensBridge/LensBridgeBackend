package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.board.request.ContentBundleRequest;
import com.ibrasoft.lensbridge.dto.board.response.AgentWeatherResponse;
import com.ibrasoft.lensbridge.dto.board.response.SigningKeysResponse;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.service.OpenWeatherService;
import com.ibrasoft.lensbridge.service.agent.http.AuthenticatedDevice;
import com.ibrasoft.lensbridge.service.board.offline.ContentSigningService;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundle;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;

/**
 * Endpoints a board's agent calls on its own behalf once enrolled: fetching signed content
 * for online sync, fetching the content keys to pin, and fetching current weather. See
 * MusallahBoard {@code agent/docs/architecture.md}, sections 9.1 to 9.3.
 * <p>
 * All paths are {@code permitAll} in {@code WebSecurityConfig}. The signing keys are public
 * by nature; the content bundle and weather authenticate the device themselves with
 * {@link DeviceRequestAuthenticator}, which needs the raw body and so cannot sit behind the
 * JWT filter chain.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentContentController {

    private final OfflineBundleService offlineBundleService;
    private final ContentSigningService signingService;
    private final OpenWeatherService openWeatherService;

    /**
     * The device is authenticated before the body is even bound (see {@link AuthenticatedDevice}),
     * so the order of checks is: device (401), body (400), building (503 without a signing key,
     * 502 for an unfetchable poster).
     * <p>
     * Like the admin download, the package is built completely and then written straight to
     * the servlet response, so every failure is still a JSON error rather than a truncated zip.
     * No {@code produces} on the mapping, for the same reason: it would stop Spring rendering
     * the JSON error bodies.
     * <p>
     * Device state (last seen, heartbeat) is left alone: that belongs to the WebSocket session.
     */
    @Operation(operationId = "downloadContentBundle",
            summary = "Fetch this board's signed content package (online sync)",
            description = "Device-authenticated with the X-MB-* headers (Ed25519 signature by the enrolled "
                    + "device key over musallahboard-http-v1, method, path, device id, timestamp and the "
                    + "body's SHA-256). Returns a signed .mbu content package starting today in the device's "
                    + "timezone. Media whose SHA-256 is in haveMedia is listed and signed in mbu.json but "
                    + "left out of the zip.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The signed content package",
                    content = @Content(mediaType = OfflineBundleService.MBU_CONTENT_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Malformed body, days outside 1-31, or a bad haveMedia entry",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "502", description = "A poster image could not be fetched; the message names the poster",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "503", description = "No content signing key is configured on the server",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/content-bundle")
    public void contentBundle(@AuthenticatedDevice Device device,
                              @Valid @RequestBody(required = false) ContentBundleRequest body,
                              HttpServletResponse response) throws IOException {
        ContentBundleRequest request = body == null ? new ContentBundleRequest() : body;
        OfflineBundle bundle = offlineBundleService.build(device.getId(), request.daysOrDefault(), request.haveMediaSet());
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(OfflineBundleService.MBU_CONTENT_TYPE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(bundle.filename()).build().toString());
        bundle.writeTo(response.getOutputStream());
        response.flushBuffer();
    }

    @Operation(operationId = "getSigningKeys",
            summary = "Public content signing keys boards should trust",
            description = "Current key first, then previous keys still valid during a rotation. Empty when "
                    + "the server has no content signing key. Used by `musallahboard-agent trust fetch` "
                    + "on boards enrolled before signed content existed.")
    @SecurityRequirements
    @GetMapping("/signing-keys")
    public ResponseEntity<SigningKeysResponse> signingKeys() {
        return ResponseEntity.ok(new SigningKeysResponse(signingService.publicKeys()));
    }

    /**
     * Current weather for the board to overlay on its installed content. Weather is never in a
     * content package (packages are built days ahead), so this is the one live fetch a board
     * makes.
     * <p>
     * Unavailable weather is a 200 with {@code "weather": null}, never a 5xx: a board without a
     * forecast just hides the widget.
     */
    @Operation(operationId = "getAgentWeather",
            summary = "Current weather for this board",
            description = "Device-authenticated with the X-MB-* headers, like the content bundle; the signed "
                    + "body hash is the SHA-256 of the empty string. `weather` is the OpenWeatherMap current "
                    + "weather JSON exactly as the server last fetched it, or null when the server has none. "
                    + "`fetchedAt` is when the server answered.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current weather, or null weather when unavailable",
                    content = @Content(schema = @Schema(implementation = AgentWeatherResponse.class)))
    })
    @GetMapping("/weather")
    public ResponseEntity<AgentWeatherResponse> weather(@AuthenticatedDevice Device device) {
        return ResponseEntity.ok(new AgentWeatherResponse(currentWeather(), Instant.now()));
    }

    /** {@link OpenWeatherService#getCurrentWeather()} should never throw; if it does, no weather. */
    private JsonNode currentWeather() {
        try {
            return openWeatherService.getCurrentWeather();
        } catch (RuntimeException e) {
            log.warn("Weather lookup failed; answering with null weather", e);
            return null;
        }
    }
}
