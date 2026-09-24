package com.ibrasoft.lensbridge.service.agent.http;

import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.Ed25519TestUtil;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Device HTTP authentication (architecture.md section 9.2) against a real Ed25519 key pair
 * and a fixed clock.
 */
class DeviceRequestAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-09-24T14:02:11.123Z");
    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b");
    private static final String PATH = "/api/agent/content-bundle";
    private static final byte[] BODY = "{\"days\":7,\"haveMedia\":[]}".getBytes(StandardCharsets.UTF_8);

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final DeviceRequestAuthenticator authenticator = new DeviceRequestAuthenticator(
            deviceRepository, new Ed25519Verifier(), Clock.fixed(NOW, ZoneOffset.UTC));

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
    }

    /** A request exactly as a well-behaved agent sends it. */
    private MockHttpServletRequest signed(String method, String path, long timestamp, byte[] signedBody) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(DeviceRequestAuthenticator.DEVICE_ID_HEADER, DEVICE_ID.toString());
        request.addHeader(DeviceRequestAuthenticator.TIMESTAMP_HEADER, Long.toString(timestamp));
        byte[] message = DeviceRequestAuthenticator.message(method, path, DEVICE_ID, timestamp, signedBody);
        request.addHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER,
                Base64.getEncoder().encodeToString(Ed25519TestUtil.sign(keys.getPrivate(), message)));
        return request;
    }

    private void assertRejected(MockHttpServletRequest request, byte[] body, String reason) {
        assertThatThrownBy(() -> authenticator.authenticate(request, body))
                .isInstanceOfSatisfying(ApiResponseException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(((MessageResponse) e.getBody()).getMessage())
                            .startsWith("Device authentication failed: ")
                            .contains(reason);
                });
    }

    @Test
    void theSignedMessageIsExactlyTheDocumentedLayout() {
        byte[] message = DeviceRequestAuthenticator.message("POST", PATH, DEVICE_ID, 1790258531000L, new byte[0]);

        assertThat(new String(message, StandardCharsets.UTF_8)).isEqualTo(
                "musallahboard-http-v1\n"
                        + "POST\n"
                        + "/api/agent/content-bundle\n"
                        + "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b\n"
                        + "1790258531000\n"
                        // SHA-256 of the empty string
                        + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void aCorrectlySignedRequestReturnsTheDevice() {
        assertThat(authenticator.authenticate(signed("POST", PATH, NOW.toEpochMilli(), BODY), BODY))
                .isSameAs(device);
    }

    @Test
    void anEmptyBodyIsHashedAsTheEmptyString() {
        assertThat(authenticator.authenticate(signed("GET", PATH, NOW.toEpochMilli(), new byte[0]), null))
                .isSameAs(device);
    }

    @Test
    void timestampsUpToFiveMinutesEitherWayAreAccepted() {
        long fiveMinutes = 5 * 60 * 1000L;
        for (long ts : new long[]{NOW.toEpochMilli() - fiveMinutes, NOW.toEpochMilli() + fiveMinutes}) {
            assertThat(authenticator.authenticate(signed("POST", PATH, ts, BODY), BODY)).isSameAs(device);
        }
    }

    @Test
    void timestampsFurtherOffAreRejected() {
        long justOver = 5 * 60 * 1000L + 1;
        assertRejected(signed("POST", PATH, NOW.toEpochMilli() - justOver, BODY), BODY, "server clock");
        assertRejected(signed("POST", PATH, NOW.toEpochMilli() + justOver, BODY), BODY, "server clock");
    }

    @Test
    void aTamperedBodyIsRejected() {
        byte[] tampered = "{\"days\":31,\"haveMedia\":[]}".getBytes(StandardCharsets.UTF_8);
        assertRejected(signed("POST", PATH, NOW.toEpochMilli(), BODY), tampered, "bad signature");
    }

    @Test
    void aSignatureForAnotherPathOrMethodIsRejected() {
        MockHttpServletRequest otherPath = signed("POST", "/api/agent/other", NOW.toEpochMilli(), BODY);
        otherPath.setRequestURI(PATH);
        assertRejected(otherPath, BODY, "bad signature");

        MockHttpServletRequest otherMethod = signed("GET", PATH, NOW.toEpochMilli(), BODY);
        otherMethod.setMethod("POST");
        assertRejected(otherMethod, BODY, "bad signature");
    }

    @Test
    void aSignatureByAnotherKeyIsRejected() {
        device.setPublicKey(Ed25519TestUtil.rawPublicKey(Ed25519TestUtil.generate().getPublic()));
        assertRejected(signed("POST", PATH, NOW.toEpochMilli(), BODY), BODY, "bad signature");
    }

    @Test
    void revokedUnknownAndKeylessDevicesAreRejectedAlike() {
        device.setRevokedAt(NOW.minusSeconds(60));
        assertRejected(signed("POST", PATH, NOW.toEpochMilli(), BODY), BODY, "unknown or revoked device");

        device.setRevokedAt(null);
        device.setPublicKey(null);
        assertRejected(signed("POST", PATH, NOW.toEpochMilli(), BODY), BODY, "unknown or revoked device");

        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        assertRejected(signed("POST", PATH, NOW.toEpochMilli(), BODY), BODY, "unknown or revoked device");
    }

    @Test
    void missingOrMalformedHeadersAreRejectedBeforeTheDatabase() {
        DeviceRepository untouched = mock(DeviceRepository.class);
        DeviceRequestAuthenticator strict = new DeviceRequestAuthenticator(
                untouched, new Ed25519Verifier(), Clock.fixed(NOW, ZoneOffset.UTC));

        MockHttpServletRequest none = new MockHttpServletRequest("POST", PATH);
        assertThatThrownBy(() -> strict.authenticate(none, BODY)).isInstanceOf(ApiResponseException.class);

        MockHttpServletRequest badId = signed("POST", PATH, NOW.toEpochMilli(), BODY);
        badId.removeHeader(DeviceRequestAuthenticator.DEVICE_ID_HEADER);
        badId.addHeader(DeviceRequestAuthenticator.DEVICE_ID_HEADER, "board-1");
        assertThatThrownBy(() -> strict.authenticate(badId, BODY))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(((MessageResponse) e.getBody()).getMessage()).contains("not a UUID"));

        MockHttpServletRequest badTs = signed("POST", PATH, NOW.toEpochMilli(), BODY);
        badTs.removeHeader(DeviceRequestAuthenticator.TIMESTAMP_HEADER);
        badTs.addHeader(DeviceRequestAuthenticator.TIMESTAMP_HEADER, "2026-09-24T14:02:11Z");
        assertThatThrownBy(() -> strict.authenticate(badTs, BODY))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(((MessageResponse) e.getBody()).getMessage()).contains("Unix milliseconds"));

        MockHttpServletRequest badSig = signed("POST", PATH, NOW.toEpochMilli(), BODY);
        badSig.removeHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER);
        badSig.addHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER, "***");
        assertThatThrownBy(() -> strict.authenticate(badSig, BODY))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(((MessageResponse) e.getBody()).getMessage()).contains("not base64"));

        verifyNoInteractions(untouched);
    }
}
