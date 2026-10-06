package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.OpenWeatherService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.agent.DeviceEnrollmentService;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import com.ibrasoft.lensbridge.service.agent.http.AuthenticatedDeviceArgumentResolver;
import com.ibrasoft.lensbridge.service.agent.http.DeviceAuthInterceptor;
import com.ibrasoft.lensbridge.service.agent.http.DeviceBodyCachingFilter;
import com.ibrasoft.lensbridge.service.agent.http.DeviceRequestAuthenticator;
import com.ibrasoft.lensbridge.service.board.offline.ContentSigningService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/agent/enroll}: request validation and the address recorded for the device.
 * The happy-path response shape is covered in {@link AgentContentControllerTest}.
 */
@WebMvcTest(controllers = AgentEnrollmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DeviceRequestAuthenticator.class, Ed25519Verifier.class, DeviceAuthInterceptor.class,
        AuthenticatedDeviceArgumentResolver.class, DeviceBodyCachingFilter.class})
@TestPropertySource(properties = "musallahboard.agent.websocketUrl=wss://example.test/api/agent/ws")
class AgentEnrollmentControllerTest {

    private static final String URL = "/api/agent/enroll";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceEnrollmentService enrollmentService;
    @MockitoBean
    private ContentSigningService signingService;
    // The device-auth interceptor and WebConfig's argument resolver need these in the slice.
    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private OpenWeatherService openWeatherService;
    @MockitoBean
    private UserService userService;

    private static String body(String hostname, String hardwareModel, String agentVersion) {
        return "{\"token\":\"t\",\"publicKey\":\"k\",\"hostname\":\"" + hostname + "\","
                + "\"hardwareModel\":\"" + hardwareModel + "\",\"agentVersion\":\"" + agentVersion + "\"}";
    }

    /** These land in varchar(255) columns; Postgres would turn an over-long value into a 500. */
    @Test
    void rejectsAnOverlongHostnameAsABadRequest() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("h".repeat(256), "RPi4", "1.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("hostname")));

        verifyNoInteractions(enrollmentService);
    }

    @Test
    void rejectsAnOverlongHardwareModelAsABadRequest() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("kiosk", "m".repeat(256), "1.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("hardwareModel")));

        verifyNoInteractions(enrollmentService);
    }

    @Test
    void rejectsAnOverlongAgentVersionAsABadRequest() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("kiosk", "RPi4", "v".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("agentVersion")));

        verifyNoInteractions(enrollmentService);
    }

    @Test
    void acceptsValuesExactlyAtTheColumnWidth() throws Exception {
        when(enrollmentService.enroll(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DeviceEnrollmentService.Outcome.Ok(
                        Device.builder().id(UUID.randomUUID()).displayName("d").audience(Audience.BOTH).build()));
        when(signingService.publicKeys()).thenReturn(List.of());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("h".repeat(255), "m".repeat(255), "v".repeat(255))))
                .andExpect(status().isOk());
    }

    /**
     * The first X-Forwarded-For entry is whatever the client chose to send. The address the
     * container resolved (the socket peer, or the trusted proxy's view of it) is the one to keep.
     */
    @Test
    void recordsTheResolvedRemoteAddressNotTheForwardedForHeader() throws Exception {
        when(enrollmentService.enroll(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DeviceEnrollmentService.Outcome.Ok(
                        Device.builder().id(UUID.randomUUID()).displayName("d").audience(Audience.BOTH).build()));
        when(signingService.publicKeys()).thenReturn(List.of());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.99, 10.0.0.1")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.7");
                            return request;
                        })
                        .content(body("kiosk", "RPi4", "1.0")))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(enrollmentService).enroll(any(), any(), any(), any(), any(), ip.capture());
        assertThat(ip.getValue()).isEqualTo("198.51.100.7");
    }
}
