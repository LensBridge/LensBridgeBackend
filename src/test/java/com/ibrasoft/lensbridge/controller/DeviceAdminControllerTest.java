package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.AgentSession;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceAdminControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revoke_closesRegisteredAgentSession() throws Exception {
        EnrollmentTokenService enrollmentTokenService = mock(EnrollmentTokenService.class);
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        DeviceCommandRepository commandRepository = mock(DeviceCommandRepository.class);
        CommandDispatcher commandDispatcher = mock(CommandDispatcher.class);
        AgentSessionRegistry registry = new AgentSessionRegistry();
        BoardStreamHandler boardStream = mock(BoardStreamHandler.class);
        AdminAuditService auditService = mock(AdminAuditService.class);

        DeviceAdminController controller = new DeviceAdminController(
                enrollmentTokenService,
                deviceRepository,
                commandRepository,
                commandDispatcher,
                new ObjectMapper(),
                registry,
                boardStream,
                auditService);

        UUID deviceId = UUID.randomUUID();
        Device device = Device.builder()
                .id(deviceId)
                .displayName("Lobby board")
                .audience(Audience.BOTH)
                .build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebSocketSession transport = mock(WebSocketSession.class);
        when(transport.isOpen()).thenReturn(true);
        AgentSession session = new AgentSession(transport, "challenge-base64", new ObjectMapper());
        session.markAuthenticated(deviceId);
        registry.register(deviceId, session);

        setCurrentUser();

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("10.0.0.1");

        controller.revoke(deviceId, httpRequest);

        assertNotNull(device.getRevokedAt());
        assertTrue(registry.get(deviceId).isEmpty());
        verify(transport).close(argThat(status ->
                status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
                        && "device_revoked".equals(status.getReason())));
        // Revocation must drop the refresh stream too, not just the command channel —
        // otherwise a revoked board keeps receiving pushes until it happens to reconnect.
        verify(boardStream).closeIfPresent(eq(deviceId), argThat(status ->
                status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
                        && "device_revoked".equals(status.getReason())));
        verify(auditService).logAuditEvent(
                eq("root@example.com"), eq(AuditAction.REVOKE_DEVICE),
                eq("Device"), eq(deviceId), eq("10.0.0.1"));
    }

    @Test
    void revoke_isANoOpAndDoesNotReAuditWhenAlreadyRevoked() throws Exception {
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        AdminAuditService auditService = mock(AdminAuditService.class);

        DeviceAdminController controller = new DeviceAdminController(
                mock(EnrollmentTokenService.class),
                deviceRepository,
                mock(DeviceCommandRepository.class),
                mock(CommandDispatcher.class),
                new ObjectMapper(),
                new AgentSessionRegistry(),
                mock(BoardStreamHandler.class),
                auditService);

        UUID deviceId = UUID.randomUUID();
        Instant revokedAt = Instant.now().minusSeconds(3600);
        Device device = Device.builder()
                .id(deviceId)
                .displayName("Lobby board")
                .audience(Audience.BOTH)
                .revokedAt(revokedAt)
                .build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        setCurrentUser();
        controller.revoke(deviceId, new MockHttpServletRequest());

        assertEquals(revokedAt, device.getRevokedAt());
        verify(deviceRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    private static void setCurrentUser() {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ROOT"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("root@example.com", null, authorities));
    }
}
