package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.agent.AgentSession;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

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

        DeviceAdminController controller = new DeviceAdminController(
                enrollmentTokenService,
                deviceRepository,
                commandRepository,
                commandDispatcher,
                new ObjectMapper(),
                registry);

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

        controller.revoke(deviceId);

        assertNotNull(device.getRevokedAt());
        assertTrue(registry.get(deviceId).isEmpty());
        verify(transport).close(argThat(status ->
                status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
                        && "device_revoked".equals(status.getReason())));
    }

    private static void setCurrentUser() {
        UserDetailsImpl user = new UserDetailsImpl(
                UUID.randomUUID(),
                "Root",
                "Admin",
                "root@example.com",
                "1000000000",
                "password",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ROOT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
