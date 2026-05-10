package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.request.AgentEnrollRequest;
import com.ibrasoft.lensbridge.dto.board.response.AgentEnrollResponse;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.service.agent.DeviceEnrollmentService;
import com.ibrasoft.lensbridge.service.agent.DeviceEnrollmentService.Outcome;
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

/**
 * Public endpoint used by a freshly-flashed agent to convert a one-time enrollment token
 * into a Device row + Ed25519 public-key registration.
 * <p>
 * No auth required (the token is the credential), but rate-limiting belongs at the gateway.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentEnrollmentController {

    private final DeviceEnrollmentService enrollmentService;

    @Value("${musallahboard.agent.websocketUrl:ws://10.0.0.75/api/agent/ws}")
    private String websocketUrl;

    @PostMapping("/enroll")
    public ResponseEntity<?> enroll(@Valid @RequestBody AgentEnrollRequest request,
                                    HttpServletRequest httpRequest) {
        String remoteIp = resolveClientIp(httpRequest);

        Outcome outcome = enrollmentService.enroll(
                request.getToken(),
                request.getPublicKey(),
                request.getHostname(),
                request.getHardwareModel(),
                request.getAgentVersion(),
                remoteIp
        );

        return switch (outcome) {
            case Outcome.Ok ok -> ResponseEntity.ok(AgentEnrollResponse.builder()
                    .deviceId(ok.device().getId())
                    .websocketUrl(websocketUrl)
                    .build());
            case Outcome.InvalidPublicKey bad -> ResponseEntity.badRequest()
                    .body(new MessageResponse("Invalid public key: " + bad.reason()));
            case Outcome.InvalidToken ignored -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Enrollment token is invalid, expired, or already used"));
        };
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
