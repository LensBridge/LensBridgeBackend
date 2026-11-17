package com.ibrasoft.lensbridge.handler;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.Ed25519TestUtil;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import com.ibrasoft.lensbridge.service.agent.http.DeviceRequestAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The upgrade of {@code /api/refresh-musallahboard}: device signatures are verified with the
 * real {@link DeviceRequestAuthenticator}, and unsigned upgrades are let through only while
 * {@code musallahboard.board-stream.require-signature} is off.
 */
class BoardStreamHandshakeInterceptorTest {

    private static final String PATH = "/api/refresh-musallahboard";
    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b");

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final DeviceRequestAuthenticator authenticator =
            new DeviceRequestAuthenticator(deviceRepository, new Ed25519Verifier());
    private KeyPair keys;
    private Device device;

    @BeforeEach
    void setUp() {
        keys = Ed25519TestUtil.generate();
        device = Device.builder().id(DEVICE_ID).displayName("Lobby board").audience(Audience.BOTH)
                .publicKey(Ed25519TestUtil.rawPublicKey(keys.getPublic())).build();
        when(deviceRepository.findById(any())).thenReturn(Optional.empty());
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
    }

    // ── signed upgrades ───────────────────────────────────────────────────

    @Test
    void validSignatureIsAcceptedAndRecordsTheDevice() {
        Result r = handshake(interceptor(false), signed("deviceId=" + DEVICE_ID, now()));

        assertThat(r.accepted).isTrue();
        assertThat(r.attributes).containsEntry(BoardStreamHandshakeInterceptor.SIGNED_DEVICE_ATTR, DEVICE_ID);
    }

    @Test
    void signedUpgradeNeedsNoQuery() {
        Result r = handshake(interceptor(true), signed(null, now()));

        assertThat(r.accepted).isTrue();
        assertThat(r.attributes).containsEntry(BoardStreamHandshakeInterceptor.SIGNED_DEVICE_ATTR, DEVICE_ID);
    }

    /** A present signature is checked whatever the flag says: being lenient is for its absence only. */
    @Test
    void badSignatureIsRefusedEvenWhileUnsignedUpgradesAreAllowed() {
        MockHttpServletRequest request = signed("deviceId=" + DEVICE_ID, now());
        byte[] other = Ed25519TestUtil.sign(Ed25519TestUtil.generate().getPrivate(), new byte[]{1});
        request.removeHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER);
        request.addHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER, Base64.getEncoder().encodeToString(other));

        Result r = handshake(interceptor(false), request);

        assertThat(r.accepted).isFalse();
        assertThat(r.status).isEqualTo(401);
        assertThat(r.attributes).isEmpty();
    }

    @Test
    void partialSignatureHeadersCountAsASignatureAndAreRefused() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setQueryString("deviceId=" + DEVICE_ID);
        request.addHeader(DeviceRequestAuthenticator.DEVICE_ID_HEADER, DEVICE_ID.toString());

        Result r = handshake(interceptor(false), request);

        assertThat(r.accepted).isFalse();
        assertThat(r.status).isEqualTo(401);
    }

    @Test
    void signatureForAnotherPathIsRefused() {
        long ts = now();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        sign(request, "/api/agent/weather", ts);

        assertThat(handshake(interceptor(false), request).status).isEqualTo(401);
    }

    @Test
    void staleTimestampIsRefused() {
        Result r = handshake(interceptor(false), signed(null, now() - 6 * 60_000));

        assertThat(r.accepted).isFalse();
        assertThat(r.status).isEqualTo(401);
    }

    @Test
    void revokedDeviceIsRefused() {
        device.setRevokedAt(Instant.now());

        assertThat(handshake(interceptor(false), signed(null, now())).status).isEqualTo(401);
    }

    /** The query is not signed, so it may not name a different device than the signature. */
    @Test
    void queryNamingAnotherDeviceIsRefused() {
        Result r = handshake(interceptor(false), signed("deviceId=" + UUID.randomUUID(), now()));

        assertThat(r.accepted).isFalse();
        assertThat(r.status).isEqualTo(400);
    }

    /** Replaying a captured upgrade inside the five-minute window would evict the board. */
    @Test
    void replayedUpgradeIsRefused() {
        BoardStreamHandshakeInterceptor interceptor = interceptor(false);
        long ts = now();

        assertThat(handshake(interceptor, signed(null, ts)).accepted).isTrue();
        Result replay = handshake(interceptor, signed(null, ts));
        assertThat(replay.accepted).isFalse();
        assertThat(replay.status).isEqualTo(401);
        assertThat(handshake(interceptor, signed(null, ts - 1)).accepted).isFalse();

        assertThat(handshake(interceptor, signed(null, ts + 1)).accepted).isTrue();
    }

    @Test
    void refusedSignatureDoesNotAdvanceTheReplayGuard() {
        BoardStreamHandshakeInterceptor interceptor = interceptor(false);
        long ts = now();
        MockHttpServletRequest forged = signed(null, ts);
        forged.removeHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER);
        forged.addHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER,
                Base64.getEncoder().encodeToString(new byte[64]));

        assertThat(handshake(interceptor, forged).accepted).isFalse();
        assertThat(handshake(interceptor, signed(null, ts)).accepted).isTrue();
    }

    // ── unsigned upgrades ─────────────────────────────────────────────────

    @Test
    void unsignedUpgradeIsAcceptedWhileSignaturesAreNotRequired() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setQueryString("deviceId=" + DEVICE_ID);

        Result r = handshake(interceptor(false), request);

        assertThat(r.accepted).isTrue();
        // No proven device: the handler treats it as an unsigned claim.
        assertThat(r.attributes).doesNotContainKey(BoardStreamHandshakeInterceptor.SIGNED_DEVICE_ATTR);
    }

    @Test
    void unsignedUpgradeIsRefusedOnceSignaturesAreRequired() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setQueryString("deviceId=" + DEVICE_ID);

        Result r = handshake(interceptor(true), request);

        assertThat(r.accepted).isFalse();
        assertThat(r.status).isEqualTo(401);
    }

    // ── agreement with the agent ──────────────────────────────────────────

    /**
     * The same vector as the agent's {@code TestRefreshHeaderSignedGolden}
     * (internal/boardsync/boardsync_test.go): its device key is the Ed25519 seed of 32 bytes
     * of 7, and the signature below is exactly what the agent sends on the refresh upgrade at
     * that time. If either side changes the signed message, one of the two tests fails.
     */
    @Test
    void agentGoldenVectorVerifies() {
        long ts = 1790258531123L;
        Device agentDevice = Device.builder().id(DEVICE_ID).displayName("golden").audience(Audience.BOTH)
                .publicKey(Base64.getDecoder().decode("6kpsY+KcUgq+9VB7Ey7F+ZVHdq6+vnuSQh7qaRRG0iw="))
                .build();
        DeviceRepository repository = mock(DeviceRepository.class);
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.of(agentDevice));
        DeviceRequestAuthenticator fixedClock = new DeviceRequestAuthenticator(repository, new Ed25519Verifier(),
                Clock.fixed(Instant.ofEpochMilli(ts), ZoneOffset.UTC));
        BoardStreamHandshakeInterceptor interceptor = new BoardStreamHandshakeInterceptor(fixedClock, true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setQueryString("deviceId=" + DEVICE_ID);
        request.addHeader("User-Agent", "musallahboard-agent/0.3.0");
        request.addHeader(DeviceRequestAuthenticator.DEVICE_ID_HEADER, DEVICE_ID.toString());
        request.addHeader(DeviceRequestAuthenticator.TIMESTAMP_HEADER, Long.toString(ts));
        request.addHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER,
                "TGTiOMTSt8N/7ad06yMvmWo9a68Yz01LNGYnDOPGfedeiPTPjC9hqtKa/yLAOa7rXlDa5UfKTpmI28tdSME4Dg==");

        Result r = handshake(interceptor, request);

        assertThat(r.accepted).isTrue();
        assertThat(r.attributes).containsEntry(BoardStreamHandshakeInterceptor.SIGNED_DEVICE_ATTR, DEVICE_ID);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private BoardStreamHandshakeInterceptor interceptor(boolean requireSignature) {
        return new BoardStreamHandshakeInterceptor(authenticator, requireSignature);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** An upgrade signed exactly as the agent signs it: GET, the path, an empty body. */
    private MockHttpServletRequest signed(String query, long timestamp) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setQueryString(query);
        sign(request, PATH, timestamp);
        return request;
    }

    private void sign(MockHttpServletRequest request, String signedPath, long timestamp) {
        byte[] message = DeviceRequestAuthenticator.message("GET", signedPath, DEVICE_ID, timestamp, new byte[0]);
        request.addHeader(DeviceRequestAuthenticator.DEVICE_ID_HEADER, DEVICE_ID.toString());
        request.addHeader(DeviceRequestAuthenticator.TIMESTAMP_HEADER, Long.toString(timestamp));
        request.addHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER,
                Base64.getEncoder().encodeToString(Ed25519TestUtil.sign(keys.getPrivate(), message)));
    }

    private static Result handshake(BoardStreamHandshakeInterceptor interceptor, MockHttpServletRequest request) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), mock(WebSocketHandler.class), attributes);
        return new Result(accepted, response.getStatus(), attributes);
    }

    private record Result(boolean accepted, int status, Map<String, Object> attributes) {
    }
}
