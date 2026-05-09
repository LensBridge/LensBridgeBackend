package com.ibrasoft.lensbridge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.Ed25519TestUtil;
import com.ibrasoft.lensbridge.service.agent.HeartbeatService;
import com.ibrasoft.lensbridge.service.agent.handshake.AuthSignaturePayload;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentWebSocketHandlerTest {

    private ObjectMapper mapper;
    private DeviceRepository deviceRepo;
    private AgentSessionRegistry registry;
    private HeartbeatService heartbeatService;
    private AgentWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        deviceRepo = mock(DeviceRepository.class);
        registry = new AgentSessionRegistry();
        heartbeatService = mock(HeartbeatService.class);
        handler = new AgentWebSocketHandler(mapper, new Ed25519Verifier(), deviceRepo, registry, heartbeatService);
    }

    @Test
    void afterConnectionEstablished_sendsHello() throws Exception {
        FakeSession s = new FakeSession();

        handler.afterConnectionEstablished(s);

        assertEquals(1, s.outbox.size());
        JsonNode hello = mapper.readTree(s.outbox.get(0));
        assertEquals("hello", hello.get("type").asText());
        assertEquals(1, hello.get("seq").asLong());
        assertNotNull(UUID.fromString(hello.get("sessionId").asText()));
        assertTrue(hello.get("challenge").asText().length() >= 40, "expected base64-encoded 32 random bytes");
        assertTrue(hello.get("serverTime").asLong() > 0);
    }

    @Test
    void auth_happyPath_sendsAuthOkAndRegisters() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = persistedDeviceWith(Ed25519TestUtil.rawPublicKey(kp.getPublic()));

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));

        long now = System.currentTimeMillis();
        deliverAuth(s, hello.sessionId, hello.challenge, device.getId(), now, kp);

        assertTrue(s.isOpen, "session should remain open after successful auth");
        assertEquals(2, s.outbox.size(), "expected auth_ok in addition to hello");
        JsonNode ok = mapper.readTree(s.outbox.get(1));
        assertEquals("auth_ok", ok.get("type").asText());
        assertEquals(2, ok.get("seq").asLong());
        assertEquals(device.getId().toString(), ok.get("deviceId").asText());
        assertEquals(30_000, ok.get("heartbeatIntervalMs").asInt());

        assertTrue(registry.get(device.getId()).isPresent(), "registry should hold the new session");
    }

    @Test
    void auth_badSignature_closes4001() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = persistedDeviceWith(Ed25519TestUtil.rawPublicKey(kp.getPublic()));

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));

        long now = System.currentTimeMillis();
        // Sign the WRONG message (different challenge).
        byte[] wrongPayload = AuthSignaturePayload.build(hello.sessionId, "different-challenge", device.getId(), now);
        String wrongSig = Base64.getEncoder().encodeToString(Ed25519TestUtil.sign(kp.getPrivate(), wrongPayload));

        deliver(s, authJson(hello.sessionId, device.getId(), now, wrongSig));

        assertClosed(s, 4001);
        assertTrue(registry.get(device.getId()).isEmpty());
    }

    @Test
    void auth_expiredTimestamp_closes4001() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = persistedDeviceWith(Ed25519TestUtil.rawPublicKey(kp.getPublic()));

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));

        long stale = System.currentTimeMillis() - (10 * 60 * 1000L); // 10 minutes ago
        deliverAuth(s, hello.sessionId, hello.challenge, device.getId(), stale, kp);

        assertClosed(s, 4001);
    }

    @Test
    void auth_revokedDevice_closes4001() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = persistedDeviceWith(Ed25519TestUtil.rawPublicKey(kp.getPublic()));
        device.setRevokedAt(java.time.Instant.now().minusSeconds(60));

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));

        deliverAuth(s, hello.sessionId, hello.challenge, device.getId(), System.currentTimeMillis(), kp);

        assertClosed(s, 4001);
    }

    @Test
    void auth_unknownDevice_closes4001() throws Exception {
        when(deviceRepo.findById(any())).thenReturn(Optional.empty());

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));

        KeyPair kp = Ed25519TestUtil.generate();
        deliverAuth(s, hello.sessionId, hello.challenge, UUID.randomUUID(), System.currentTimeMillis(), kp);

        assertClosed(s, 4001);
    }

    @Test
    void auth_sessionIdMismatch_closes4001() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = persistedDeviceWith(Ed25519TestUtil.rawPublicKey(kp.getPublic()));

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));

        // Defends against an attacker replaying a frame captured from a different session.
        UUID forgedSessionId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        byte[] payload = AuthSignaturePayload.build(forgedSessionId, hello.challenge, device.getId(), now);
        String sig = Base64.getEncoder().encodeToString(Ed25519TestUtil.sign(kp.getPrivate(), payload));

        deliver(s, authJson(forgedSessionId, device.getId(), now, sig));

        assertClosed(s, 4001);
    }

    @Test
    void duplicateSeq_closes4002() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = persistedDeviceWith(Ed25519TestUtil.rawPublicKey(kp.getPublic()));

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));

        long now = System.currentTimeMillis();
        deliverAuth(s, hello.sessionId, hello.challenge, device.getId(), now, kp);
        assertTrue(s.isOpen, "auth should have succeeded first");

        String hbReplay = String.format(
                "{\"type\":\"heartbeat\",\"seq\":1,\"sessionId\":\"%s\",\"telemetry\":{}}",
                hello.sessionId);
        deliver(s, hbReplay);

        assertClosed(s, 4002);
    }

    @Test
    void heartbeat_persistsViaService() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = persistedDeviceWith(Ed25519TestUtil.rawPublicKey(kp.getPublic()));

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));
        deliverAuth(s, hello.sessionId, hello.challenge, device.getId(), System.currentTimeMillis(), kp);

        String hb = String.format(
                "{\"type\":\"heartbeat\",\"seq\":2,\"sessionId\":\"%s\"," +
                        "\"telemetry\":{\"uptimeSec\":42,\"cpuTempC\":50.5,\"kioskAlive\":true}}",
                hello.sessionId);
        deliver(s, hb);

        verify(heartbeatService).record(eq(device.getId()), any(), any());
        assertTrue(s.isOpen, "heartbeat should not close session");
    }

    @Test
    void afterClose_unregistersFromRegistry() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = persistedDeviceWith(Ed25519TestUtil.rawPublicKey(kp.getPublic()));

        FakeSession s = openSession();
        Hello hello = parseHello(s.outbox.get(0));
        deliverAuth(s, hello.sessionId, hello.challenge, device.getId(), System.currentTimeMillis(), kp);

        assertTrue(registry.get(device.getId()).isPresent());
        handler.afterConnectionClosed(s, CloseStatus.NORMAL);
        assertTrue(registry.get(device.getId()).isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private FakeSession openSession() throws Exception {
        FakeSession s = new FakeSession();
        handler.afterConnectionEstablished(s);
        return s;
    }

    private Device persistedDeviceWith(byte[] rawPublicKey) {
        Device device = Device.builder()
                .id(UUID.randomUUID())
                .displayName("test")
                .audience(Audience.BOTH)
                .publicKey(rawPublicKey)
                .build();
        when(deviceRepo.findById(device.getId())).thenReturn(Optional.of(device));
        return device;
    }

    private record Hello(UUID sessionId, String challenge) {}

    private Hello parseHello(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return new Hello(
                UUID.fromString(node.get("sessionId").asText()),
                node.get("challenge").asText()
        );
    }

    private void deliverAuth(FakeSession s, UUID sessionId, String challenge, UUID deviceId, long timestamp, KeyPair kp) {
        byte[] payload = AuthSignaturePayload.build(sessionId, challenge, deviceId, timestamp);
        String sig = Base64.getEncoder().encodeToString(Ed25519TestUtil.sign(kp.getPrivate(), payload));
        deliver(s, authJson(sessionId, deviceId, timestamp, sig));
    }

    private void deliver(FakeSession s, String json) {
        // handleTextMessage is protected; same package access.
        handler.handleTextMessage(s, new TextMessage(json));
    }

    private String authJson(UUID sessionId, UUID deviceId, long timestamp, String signatureBase64) {
        return String.format(
                "{\"type\":\"auth\",\"seq\":1,\"sessionId\":\"%s\",\"deviceId\":\"%s\",\"timestamp\":%d,\"signature\":\"%s\"}",
                sessionId, deviceId, timestamp, signatureBase64);
    }

    private void assertClosed(FakeSession s, int expectedCode) {
        assertFalse(s.isOpen, "session should be closed");
        assertNotNull(s.closeStatus, "expected close status to be set");
        assertEquals(expectedCode, s.closeStatus.getCode(),
                "expected close code " + expectedCode + " but got " + s.closeStatus);
    }

    /** Hand-rolled WebSocketSession to avoid deep-stub mocks. */
    static class FakeSession implements WebSocketSession {
        final Map<String, Object> attributes = new HashMap<>();
        final List<String> outbox = new ArrayList<>();
        boolean isOpen = true;
        CloseStatus closeStatus;
        final String id = UUID.randomUUID().toString();

        @Override public String getId() { return id; }
        @Override public java.net.URI getUri() { return null; }
        @Override public org.springframework.http.HttpHeaders getHandshakeHeaders() { return new org.springframework.http.HttpHeaders(); }
        @Override public Map<String, Object> getAttributes() { return attributes; }
        @Override public java.security.Principal getPrincipal() { return null; }
        @Override public java.net.InetSocketAddress getLocalAddress() { return null; }
        @Override public java.net.InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int messageSizeLimit) {}
        @Override public int getTextMessageSizeLimit() { return 0; }
        @Override public void setBinaryMessageSizeLimit(int messageSizeLimit) {}
        @Override public int getBinaryMessageSizeLimit() { return 0; }
        @Override public List<org.springframework.web.socket.WebSocketExtension> getExtensions() { return List.of(); }
        @Override public void sendMessage(WebSocketMessage<?> message) {
            if (message instanceof TextMessage tm) outbox.add(tm.getPayload());
        }
        @Override public boolean isOpen() { return isOpen; }
        @Override public void close() { close(CloseStatus.NORMAL); }
        @Override public void close(CloseStatus status) {
            this.isOpen = false;
            this.closeStatus = status;
        }
    }
}
