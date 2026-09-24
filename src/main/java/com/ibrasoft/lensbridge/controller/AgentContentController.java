package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.board.request.ContentBundleRequest;
import com.ibrasoft.lensbridge.dto.board.response.SigningKeysResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.service.agent.http.DeviceRequestAuthenticator;
import com.ibrasoft.lensbridge.service.board.offline.ContentSigningService;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundle;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Endpoints a board's agent calls on its own behalf once enrolled: fetching signed content
 * for online sync, and fetching the content keys to pin. See MusallahBoard
 * {@code agent/docs/architecture.md}, sections 9.1 to 9.3.
 * <p>
 * Both paths are {@code permitAll} in {@code WebSecurityConfig}. The signing keys are public
 * by nature; the content bundle authenticates the device itself with
 * {@link DeviceRequestAuthenticator}, which needs the raw body and so cannot sit behind the
 * JWT filter chain.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentContentController {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private final DeviceRequestAuthenticator deviceAuthenticator;
    private final OfflineBundleService offlineBundleService;
    private final ContentSigningService signingService;
    private final ObjectMapper objectMapper;

    /**
     * The body is taken as raw bytes and parsed here, after authentication, so the signature
     * check hashes exactly what the device sent. Order of checks: device auth (401), then the
     * body (400), then building (404/409 in theory, 503 without a signing key, 502 for an
     * unfetchable poster).
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
    @Parameters({
            @Parameter(in = ParameterIn.HEADER, name = DeviceRequestAuthenticator.DEVICE_ID_HEADER, required = true,
                    description = "Enrolled device id", schema = @Schema(type = "string", format = "uuid")),
            @Parameter(in = ParameterIn.HEADER, name = DeviceRequestAuthenticator.TIMESTAMP_HEADER, required = true,
                    description = "Unix milliseconds; must be within 5 minutes of the server clock",
                    schema = @Schema(type = "integer", format = "int64")),
            @Parameter(in = ParameterIn.HEADER, name = DeviceRequestAuthenticator.SIGNATURE_HEADER, required = true,
                    description = "Base64 Ed25519 signature by the device key", schema = @Schema(type = "string"))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = ContentBundleRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The signed content package",
                    content = @Content(mediaType = OfflineBundleService.MBU_CONTENT_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Malformed body, days outside 1-31, or a bad haveMedia entry",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or bad device signature, clock skew over 5 minutes, "
                    + "or an unknown or revoked device",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "502", description = "A poster image could not be fetched; the message names the poster",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "503", description = "No content signing key is configured on the server",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/content-bundle")
    public void contentBundle(@RequestBody(required = false) byte[] body,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        Device device = deviceAuthenticator.authenticate(request, body);
        ContentBundleRequest parsed = parse(body);

        int days = parsed.getDays() == null ? ContentBundleRequest.DEFAULT_DAYS : parsed.getDays();
        Set<String> haveMedia = haveMedia(parsed.getHaveMedia());

        OfflineBundle bundle = offlineBundleService.build(device.getId(), days, haveMedia);
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

    /** An absent or empty body means all defaults. Unknown fields are ignored (forward compatible). */
    private ContentBundleRequest parse(byte[] body) {
        if (body == null || body.length == 0) {
            return new ContentBundleRequest();
        }
        try {
            ContentBundleRequest parsed = objectMapper.readValue(body, ContentBundleRequest.class);
            return parsed == null ? new ContentBundleRequest() : parsed;
        } catch (IOException e) {
            throw badRequest("Request body must be a JSON object like {\"days\": 7, \"haveMedia\": []}");
        }
    }

    private static Set<String> haveMedia(List<String> hashes) {
        if (hashes == null) return Set.of();
        if (hashes.size() > ContentBundleRequest.MAX_HAVE_MEDIA) {
            throw badRequest("haveMedia may list at most " + ContentBundleRequest.MAX_HAVE_MEDIA + " hashes");
        }
        Set<String> set = new LinkedHashSet<>();
        for (String hash : hashes) {
            if (hash == null || !SHA256_HEX.matcher(hash).matches()) {
                throw badRequest("haveMedia entries must be 64 lowercase hex characters (SHA-256)");
            }
            set.add(hash);
        }
        return set;
    }

    private static ApiResponseException badRequest(String message) {
        return new ApiResponseException(HttpStatus.BAD_REQUEST, new MessageResponse(message));
    }
}
