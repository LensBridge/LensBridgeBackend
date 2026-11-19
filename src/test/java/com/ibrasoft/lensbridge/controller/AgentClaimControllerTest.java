package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.OpenWeatherService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.agent.DeviceClaimService;
import com.ibrasoft.lensbridge.service.agent.DeviceEnrollmentService;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import com.ibrasoft.lensbridge.service.agent.http.AuthenticatedDeviceArgumentResolver;
import com.ibrasoft.lensbridge.service.agent.http.DeviceAuthInterceptor;
import com.ibrasoft.lensbridge.service.agent.http.DeviceBodyCachingFilter;
import com.ibrasoft.lensbridge.service.agent.http.DeviceRequestAuthenticator;
import com.ibrasoft.lensbridge.service.board.offline.ContentSigningService;
import com.ibrasoft.lensbridge.dto.board.response.SigningKeyView;
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

/** {@code POST /api/agent/claim} (contract C2): what each claim outcome looks like on the wire. */
@WebMvcTest(controllers = AgentEnrollmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DeviceRequestAuthenticator.class, Ed25519Verifier.class, DeviceAuthInterceptor.class,
        AuthenticatedDeviceArgumentResolver.class, DeviceBodyCachingFilter.class})
@TestPropertySource(properties = "musallahboard.agent.websocketUrl=wss://example.test/api/agent/ws")
class AgentClaimControllerTest {

    private static final String URL = "/api/agent/claim";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceEnrollmentService enrollmentService;
    @MockitoBean
    private DeviceClaimService claimService;
    @MockitoBean
    private ContentSigningService signingService;
    // The device-auth interceptor and WebConfig's argument resolver need these in the slice.
    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private OpenWeatherService openWeatherService;
    @MockitoBean
    private UserService userService;

    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String KEY = "gTl3Dqh9F19Wo1Rmw0x+zMuNipG07jeiXfYPW4/Js5Q=";
    private static final String PROOF = "2_nUd8LSmcq1kdXUvLW5ZQkAr_yDjiTuIh2AzpJH2sA";

    private static String body() {
        return "{\"deviceId\":\"" + DEVICE_ID + "\",\"publicKey\":\"" + KEY + "\",\"proof\":\"" + PROOF
                + "\",\"agentVersion\":\"0.5.0\",\"hostname\":\"board-1\"}";
    }

    private void claimReturns(DeviceClaimService.Outcome outcome) {
        when(claimService.claim(any(), any(), any(), any(), any(), any())).thenReturn(outcome);
    }

    @Test
    void aClaimAnswersLikeEnrollment() throws Exception {
        claimReturns(new DeviceClaimService.Outcome.Ok(Device.builder().id(DEVICE_ID)
                .displayName("North Musallah").audience(Audience.BOTH).build()));
        when(signingService.publicKeys()).thenReturn(List.of(new SigningKeyView("65b60673d6ed884b", "ebVW")));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body())
                        .with(r -> { r.setRemoteAddr("203.0.113.9"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.websocketUrl").value("wss://example.test/api/agent/ws"))
                .andExpect(jsonPath("$.contentSigningKeys[0].keyId").value("65b60673d6ed884b"));

        verify(claimService).claim(DEVICE_ID, KEY, PROOF, "0.5.0", "board-1", "203.0.113.9");
    }

    @Test
    void outcomesMapToStatuses() throws Exception {
        claimReturns(new DeviceClaimService.Outcome.Refused());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());

        claimReturns(new DeviceClaimService.Outcome.KeyConflict());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("different device key")));

        claimReturns(new DeviceClaimService.Outcome.BadRequest("publicKey must be 32 bytes, got 31"));
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("32 bytes")));
    }

    @Test
    void aClaimWithoutDeviceKeyOrProofIsRejectedBeforeTheService() throws Exception {
        for (String json : List.of(
                "{\"publicKey\":\"" + KEY + "\",\"proof\":\"" + PROOF + "\"}",
                "{\"deviceId\":\"" + DEVICE_ID + "\",\"proof\":\"" + PROOF + "\"}",
                "{\"deviceId\":\"" + DEVICE_ID + "\",\"publicKey\":\"" + KEY + "\"}",
                "{\"deviceId\":\"not-a-uuid\",\"publicKey\":\"" + KEY + "\",\"proof\":\"" + PROOF + "\"}")) {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(claimService);
    }
}
