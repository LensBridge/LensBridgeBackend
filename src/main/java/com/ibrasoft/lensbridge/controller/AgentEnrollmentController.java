package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.request.AgentClaimRequest;
import com.ibrasoft.lensbridge.dto.board.request.AgentEnrollRequest;
import com.ibrasoft.lensbridge.dto.board.response.AgentEnrollResponse;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.service.agent.DeviceClaimService;
import com.ibrasoft.lensbridge.service.agent.DeviceEnrollmentService;
import com.ibrasoft.lensbridge.service.agent.DeviceEnrollmentService.Outcome;
import com.ibrasoft.lensbridge.service.board.offline.ContentSigningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The two public endpoints through which a board binds its Ed25519 device key to a device:
 * the online claim (a board set up by an online setup file proves it holds the file's claim
 * secret) and the legacy enrollment token (a maintainer's {@code musallahboard-agent enroll}).
 * <p>
 * No user auth: the claim proof or the token is the credential. Both are rate-limited per
 * client address by {@code RateLimitingFilter}, like every {@code /api/} path.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentEnrollmentController {

    private final DeviceEnrollmentService enrollmentService;
    private final DeviceClaimService claimService;
    private final ContentSigningService signingService;

    @Value("${musallahboard.agent.websocketUrl}")
    private String websocketUrl;

    @Operation(operationId = "claimAgent",
            summary = "Bind a board's device key to its pre-created device (online setup file)",
            description = "proof is base64url(HMAC-SHA256(key = the setup file's claimSecret bytes, "
                    + "msg = deviceId + \"\\n\" + publicKey)), with the lowercase deviceId and the publicKey "
                    + "string exactly as sent. Idempotent: claiming again with the same key succeeds. "
                    + "The response is the same as enrollment's.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key bound (or already bound to this key)"),
            @ApiResponse(responseCode = "400", description = "Malformed body, public key or proof",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unknown or revoked device, no online setup file "
                    + "issued for it, or a wrong proof (a setup file older than the latest online one)",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "409", description = "The device is already bound to a different key",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/claim")
    public ResponseEntity<AgentEnrollResponse> claim(@Valid @RequestBody AgentClaimRequest request,
                                                     HttpServletRequest httpRequest) {
        DeviceClaimService.Outcome outcome = claimService.claim(
                request.getDeviceId(),
                request.getPublicKey(),
                request.getProof(),
                request.getAgentVersion(),
                request.getHostname(),
                httpRequest.getRemoteAddr());

        return switch (outcome) {
            case DeviceClaimService.Outcome.Ok ok -> ResponseEntity.ok(identity(ok.device().getId()));
            case DeviceClaimService.Outcome.BadRequest bad -> throw new ApiResponseException(HttpStatus.BAD_REQUEST,
                    new MessageResponse(bad.reason()));
            case DeviceClaimService.Outcome.Refused ignored -> throw new ApiResponseException(HttpStatus.UNAUTHORIZED,
                    new MessageResponse("Claim refused: unknown or revoked board, or its setup file is not the "
                            + "latest online one"));
            case DeviceClaimService.Outcome.KeyConflict ignored -> throw new ApiResponseException(HttpStatus.CONFLICT,
                    new MessageResponse("This board is already bound to a different device key"));
        };
    }

    @Operation(operationId = "enrollAgent",
            summary = "Exchange a one-time enrollment token for a device identity",
            description = "Called once per device by the MusallahBoard agent. The returned websocketUrl is "
                    + "persisted verbatim into the agent's config and never requested again. "
                    + "contentSigningKeys are the public content keys the agent pins in its trust store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device enrolled; identity and websocket URL returned"),
            @ApiResponse(responseCode = "400", description = "Public key malformed or unsupported",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Enrollment token invalid, expired, or already used",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/enroll")
    public ResponseEntity<AgentEnrollResponse> enroll(@Valid @RequestBody AgentEnrollRequest request,
                                    HttpServletRequest httpRequest) {
        // Not X-Forwarded-For: any client can send that header. With
        // server.forward-headers-strategy=native, Tomcat has already resolved it, from trusted
        // proxies only, and getRemoteAddr() returns that address (or the socket peer otherwise).
        String remoteIp = httpRequest.getRemoteAddr();

        Outcome outcome = enrollmentService.enroll(
                request.getToken(),
                request.getPublicKey(),
                request.getHostname(),
                request.getHardwareModel(),
                request.getAgentVersion(),
                remoteIp
        );

        return switch (outcome) {
            case Outcome.Ok ok -> ResponseEntity.ok(identity(ok.device().getId()));
            case Outcome.InvalidPublicKey bad -> throw new ApiResponseException(HttpStatus.BAD_REQUEST,
                    new MessageResponse("Invalid public key: " + bad.reason()));
            case Outcome.InvalidToken ignored -> throw new ApiResponseException(HttpStatus.UNAUTHORIZED,
                    new MessageResponse("Enrollment token is invalid, expired, or already used"));
        };
    }

    private AgentEnrollResponse identity(UUID deviceId) {
        return AgentEnrollResponse.builder()
                .deviceId(deviceId)
                .websocketUrl(websocketUrl)
                // Pinned by the agent over this same TLS connection; empty without a key.
                .contentSigningKeys(signingService.publicKeys())
                .build();
    }
}
