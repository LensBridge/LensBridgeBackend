package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.response.SigningKeyView;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.OpenWeatherService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.agent.DeviceEnrollmentService;
import com.ibrasoft.lensbridge.service.agent.Ed25519TestUtil;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import com.ibrasoft.lensbridge.service.agent.http.DeviceRequestAuthenticator;
import com.ibrasoft.lensbridge.service.board.offline.ContentSigningService;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundle;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/agent/content-bundle}, {@code GET /api/agent/weather} and
 * {@code GET /api/agent/signing-keys}, through
 * MockMvc with the real {@link DeviceRequestAuthenticator}: requests are signed with a real
 * device key exactly as the agent signs them.
 * <p>
 * Filters are off: these paths are {@code permitAll} in production, so the security chain has
 * no say, and authentication is the controller's own job, which is what this exercises.
 */
@WebMvcTest(controllers = {AgentContentController.class, AgentEnrollmentController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({DeviceRequestAuthenticator.class, Ed25519Verifier.class})
@TestPropertySource(properties = "musallahboard.agent.websocketUrl=wss://example.test/api/agent/ws")
class AgentContentControllerTest {

    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b");
    private static final String URL = "/api/agent/content-bundle";
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "0123456789abcdef".repeat(4);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OfflineBundleService offlineBundleService;
    @MockitoBean
    private ContentSigningService signingService;
    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private DeviceEnrollmentService enrollmentService;
    @MockitoBean
    private OpenWeatherService openWeatherService;
    /** Needed by WebConfig's CurrentUserArgumentResolver; see BoardAdminControllerPermissionTest. */
    @MockitoBean
    private UserService userService;

    private KeyPair keys;
    private Device device;

    @BeforeEach
    void setUp() {
        keys = Ed25519TestUtil.generate();
        device = Device.builder()
                .id(DEVICE_ID)
                .displayName("Lobby board")
                .audience(Audience.BROTHERS)
                .publicKey(Ed25519TestUtil.rawPublicKey(keys.getPublic()))
                .build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(offlineBundleService.build(any(), anyInt(), any())).thenReturn(bundle());
    }

    private static OfflineBundle bundle() {
        return new OfflineBundle("musallahboard-content-3f2a1b4c-2026-09-24.mbu", List.of(
                new OfflineBundle.Entry("mbu.json", "{}".getBytes(StandardCharsets.UTF_8), false),
                new OfflineBundle.Entry("mbu.sig", "{}".getBytes(StandardCharsets.UTF_8), false)));
    }

    /** POST {@code body} to {@code path}, signed over {@code signedBody} (normally the same bytes). */
    private MockHttpServletRequestBuilder signedPost(String path, String signedPath, byte[] body, byte[] signedBody,
                                                     long timestamp) {
        byte[] message = DeviceRequestAuthenticator.message("POST", signedPath, DEVICE_ID, timestamp, signedBody);
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(DeviceRequestAuthenticator.DEVICE_ID_HEADER, DEVICE_ID.toString())
                .header(DeviceRequestAuthenticator.TIMESTAMP_HEADER, Long.toString(timestamp))
                .header(DeviceRequestAuthenticator.SIGNATURE_HEADER,
                        Base64.getEncoder().encodeToString(Ed25519TestUtil.sign(keys.getPrivate(), message)));
    }

    private MockHttpServletRequestBuilder signedPost(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        return signedPost(URL, URL, body, body, System.currentTimeMillis());
    }

    // ==================== content-bundle ====================

    @Test
    void aSignedRequestGetsThePackageBuiltWithItsDaysAndHaveMedia() throws Exception {
        MvcResult result = mockMvc.perform(signedPost(
                        "{\"days\": 10, \"haveMedia\": [\"" + SHA_A + "\", \"" + SHA_B + "\"], \"future\": true}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.musallahboard.mbu"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"musallahboard-content-3f2a1b4c-2026-09-24.mbu\""))
                .andReturn();

        verify(offlineBundleService).build(DEVICE_ID, 10, Set.of(SHA_A, SHA_B));
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) names.add(e.getName());
        }
        assertThat(names).containsExactly("mbu.json", "mbu.sig");
    }

    @Test
    void anEmptyBodyMeansSevenDaysAndNoMedia() throws Exception {
        mockMvc.perform(signedPost(URL, URL, new byte[0], new byte[0], System.currentTimeMillis()))
                .andExpect(status().isOk());
        verify(offlineBundleService).build(DEVICE_ID, 7, Set.of());
    }

    /** The query string is not part of the signed path. */
    @Test
    void theSignedPathExcludesTheQueryString() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(signedPost(URL + "?trace=1", URL, body, body, System.currentTimeMillis()))
                .andExpect(status().isOk());
    }

    @Test
    void aBodyOtherThanTheSignedOneIsA401() throws Exception {
        byte[] signed = "{\"days\": 7}".getBytes(StandardCharsets.UTF_8);
        byte[] sent = "{\"days\": 31}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(signedPost(URL, URL, sent, signed, System.currentTimeMillis()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Device authentication failed: bad signature"));
        verifyNoInteractions(offlineBundleService);
    }

    @Test
    void aStaleTimestampIsA401() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(signedPost(URL, URL, body, body, System.currentTimeMillis() - 6 * 60 * 1000L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("server clock")));
        verifyNoInteractions(offlineBundleService);
    }

    @Test
    void aRevokedDeviceIsA401() throws Exception {
        device.setRevokedAt(java.time.Instant.now());
        mockMvc.perform(signedPost("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Device authentication failed: unknown or revoked device"));
        verifyNoInteractions(offlineBundleService);
    }

    @Test
    void noHeadersIsA401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(offlineBundleService);
    }

    @Test
    void badHaveMediaEntriesAreA400() throws Exception {
        for (String bad : new String[]{"ABC", SHA_A.toUpperCase(), SHA_A + "0", "../../etc/passwd"}) {
            mockMvc.perform(signedPost("{\"haveMedia\": [\"" + bad + "\"]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("64 lowercase hex")));
        }
        verifyNoInteractions(offlineBundleService);
    }

    @Test
    void moreThanTwoThousandHaveMediaEntriesIsA400() throws Exception {
        String list = String.join(",", java.util.Collections.nCopies(2001, "\"" + SHA_A + "\""));
        mockMvc.perform(signedPost("{\"haveMedia\": [" + list + "]}"))
                .andExpect(status().isBadRequest());

        String atCap = String.join(",", java.util.Collections.nCopies(2000, "\"" + SHA_A + "\""));
        mockMvc.perform(signedPost("{\"haveMedia\": [" + atCap + "]}"))
                .andExpect(status().isOk());
    }

    @Test
    void malformedJsonIsA400() throws Exception {
        mockMvc.perform(signedPost("{\"days\": "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(signedPost("{\"days\": \"a week\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(offlineBundleService);
    }

    @Test
    void serviceErrorsKeepTheirStatusAndJsonMessage() throws Exception {
        when(offlineBundleService.build(any(), anyInt(), any())).thenThrow(new ApiResponseException(
                HttpStatus.SERVICE_UNAVAILABLE, ErrorResponse.of("Content signing is not configured on this server")));

        mockMvc.perform(signedPost("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Content signing is not configured on this server"));
    }

    // ==================== weather ====================

    private static final String WEATHER_URL = "/api/agent/weather";

    /** GET signed as the agent signs it: no body, so the hash is of the empty string. */
    private MockHttpServletRequestBuilder signedWeatherGet(byte[] signedBody) {
        long timestamp = System.currentTimeMillis();
        byte[] message = DeviceRequestAuthenticator.message("GET", WEATHER_URL, DEVICE_ID, timestamp, signedBody);
        return get(WEATHER_URL)
                .header(DeviceRequestAuthenticator.DEVICE_ID_HEADER, DEVICE_ID.toString())
                .header(DeviceRequestAuthenticator.TIMESTAMP_HEADER, Long.toString(timestamp))
                .header(DeviceRequestAuthenticator.SIGNATURE_HEADER,
                        Base64.getEncoder().encodeToString(Ed25519TestUtil.sign(keys.getPrivate(), message)));
    }

    @Test
    void weatherReturnsTheCachedWeatherVerbatimWithFetchedAt() throws Exception {
        when(openWeatherService.getCurrentWeather()).thenReturn(new ObjectMapper().readTree(
                "{\"name\":\"Mississauga\",\"main\":{\"temp\":12.5},\"weather\":[{\"icon\":\"04d\"}]}"));
        Instant before = Instant.now();

        MvcResult result = mockMvc.perform(signedWeatherGet(new byte[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather.name").value("Mississauga"))
                .andExpect(jsonPath("$.weather.main.temp").value(12.5))
                .andExpect(jsonPath("$.weather.weather[0].icon").value("04d"))
                .andReturn();

        String fetchedAt = new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("fetchedAt").asText();
        assertThat(fetchedAt).endsWith("Z");
        assertThat(Instant.parse(fetchedAt)).isBetween(before, Instant.now());
    }

    @Test
    void weatherIsAnExplicitNullWhenTheServerHasNone() throws Exception {
        when(openWeatherService.getCurrentWeather()).thenReturn(null);

        mockMvc.perform(signedWeatherGet(new byte[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather").hasJsonPath())
                .andExpect(jsonPath("$.weather").doesNotExist())
                .andExpect(jsonPath("$.fetchedAt").isString());
    }

    @Test
    void weatherIsNullNotA5xxWhenTheServiceFails() throws Exception {
        when(openWeatherService.getCurrentWeather()).thenThrow(new IllegalStateException("upstream down"));

        mockMvc.perform(signedWeatherGet(new byte[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather").hasJsonPath())
                .andExpect(jsonPath("$.weather").doesNotExist())
                .andExpect(jsonPath("$.fetchedAt").isString());
    }

    @Test
    void weatherWithoutHeadersIsA401() throws Exception {
        mockMvc.perform(get(WEATHER_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(openWeatherService);
    }

    /** The signature must cover the empty body; one over any other body is rejected. */
    @Test
    void weatherWithABadSignatureIsA401() throws Exception {
        mockMvc.perform(signedWeatherGet("{}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Device authentication failed: bad signature"));
        verifyNoInteractions(openWeatherService);
    }

    @Test
    void weatherForARevokedDeviceIsA401() throws Exception {
        device.setRevokedAt(Instant.now());
        mockMvc.perform(signedWeatherGet(new byte[0]))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Device authentication failed: unknown or revoked device"));
        verifyNoInteractions(openWeatherService);
    }

    // ==================== signing-keys and enrollment ====================

    @Test
    void signingKeysListsTheContentKeys() throws Exception {
        when(signingService.publicKeys()).thenReturn(List.of(
                new SigningKeyView("65b60673d6ed884b", "current=="),
                new SigningKeyView("0011223344556677", "previous==")));

        mockMvc.perform(get("/api/agent/signing-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].keyId").value("65b60673d6ed884b"))
                .andExpect(jsonPath("$.content[0].publicKey").value("current=="))
                .andExpect(jsonPath("$.content[1].keyId").value("0011223344556677"));
    }

    @Test
    void signingKeysIsAnEmptyListWithoutAKey() throws Exception {
        when(signingService.publicKeys()).thenReturn(List.of());

        mockMvc.perform(get("/api/agent/signing-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void enrollmentReturnsTheContentSigningKeys() throws Exception {
        when(signingService.publicKeys()).thenReturn(List.of(new SigningKeyView("65b60673d6ed884b", "current==")));
        when(enrollmentService.enroll(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DeviceEnrollmentService.Outcome.Ok(device));

        mockMvc.perform(post("/api/agent/enroll").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"publicKey\":\"k\",\"hostname\":\"h\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.websocketUrl").value("wss://example.test/api/agent/ws"))
                .andExpect(jsonPath("$.contentSigningKeys[0].keyId").value("65b60673d6ed884b"))
                .andExpect(jsonPath("$.contentSigningKeys[0].publicKey").value("current=="));
    }
}
