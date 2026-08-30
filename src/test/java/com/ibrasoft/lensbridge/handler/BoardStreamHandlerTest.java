package com.ibrasoft.lensbridge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.Ed25519TestUtil;
import com.ibrasoft.lensbridge.service.agent.handshake.AuthSignaturePayload;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import com.ibrasoft.lensbridge.service.board.stream.BoardStreamAuthPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The refresh stream used to accept anyone who could name a device id. A device id is
 * printed in the board's own URL and listed by the admin API, so that let a stranger evict
 * a physical kiosk from the channel and read its pushes. These tests pin the replacement:
 * nothing is delivered until an Ed25519 challenge–response over the enrolled device key
 * succeeds, and everything the old handler got right about session lifecycle still holds.
 */
class BoardStreamHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private DeviceRepository deviceRepository;
    private BoardStreamHandler handler;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        when(deviceRepository.findById(any())).thenReturn(Optional.empty());
        handler = new BoardStreamHandler(mapper, new Ed25519Verifier(), deviceRepository);
    }

    // ── handshake ─────────────────────────────────────────────────────────

    @Test
    void connectingIssuesAHelloCarryingAChallenge() throws Exception {
        Session s = connect();

        assertThat(s.sent).hasSize(1);
        JsonNode hello = mapper.readTree(s.sent.get(0));
        assertThat(hello.get("type").asText()).isEqualTo("hello");
        assertThat(hello.get("seq").asLong()).isEqualTo(1);
        assertThat(UUID.fromString(hello.get("sessionId").asText())).isNotNull();
        // 32 random bytes, base64 without padding.
        assertThat(Base64.getDecoder().decode(hello.get("challenge").asText())).hasSize(32);
        assertThat(hello.get("serverTime").asLong()).isGreaterThan(0);
    }

    @Test
    void authenticatedBoardReceivesAuthOkAndThenPushes() throws Exception {
        Board board = enrolled();
        Session s = connect();

        authenticate(s, board);

        assertThat(s.open).isTrue();
        assertThat(s.sent).hasSize(2);
        JsonNode ok = mapper.readTree(s.sent.get(1));
        assertThat(ok.get("type").asText()).isEqualTo("auth_ok");
        assertThat(ok.get("seq").asLong()).isEqualTo(2);
        assertThat(ok.get("deviceId").asText()).isEqualTo(board.id.toString());

        handler.contentChanged("posters");

        assertThat(s.sent).hasSize(3);
        assertThat(mapper.readTree(s.sent.get(2)).get("type").asText()).isEqualTo("refresh");
    }

    // ── the hole this change closes ───────────────────────────────────────

    /**
     * The exact pre-fix exploit: name a real device id, receive that board's stream. The
     * device id is no longer accepted anywhere, so there is nothing to name.
     */
    @Test
    void namingARealDeviceIdWithoutSigningDeliversNothing() throws Exception {
        Board board = enrolled();
        Session attacker = connectWithQuery("deviceId=" + board.id);

        handler.contentChanged("posters");
        handler.configChanged(board.id);
        handler.refreshAll();

        // Only the hello it was issued on connect; no refresh ever reaches it.
        assertThat(attacker.sent).hasSize(1);
        assertThat(mapper.readTree(attacker.sent.get(0)).get("type").asText()).isEqualTo("hello");
    }

    /**
     * The denial-of-service half: an unauthenticated connection naming a live board's id
     * must not supersede it.
     */
    @Test
    void unauthenticatedConnectionCannotSupersedeALiveBoard() throws Exception {
        Board board = enrolled();
        Session kiosk = connect();
        authenticate(kiosk, board);

        Session attacker = connectWithQuery("deviceId=" + board.id);

        assertThat(kiosk.closedWith).isNull();
        assertThat(kiosk.open).isTrue();

        handler.configChanged(board.id);

        assertThat(mapper.readTree(kiosk.sent.get(kiosk.sent.size() - 1)).get("reason").asText())
                .isEqualTo("config");
        assertThat(attacker.sent).hasSize(1); // hello only
    }

    // ── rejected credentials ──────────────────────────────────────────────

    @Test
    void signatureFromAKeyTheDeviceDoesNotOwnIsRejected() throws Exception {
        Board board = enrolled();
        KeyPair impostor = Ed25519TestUtil.generate();
        Session s = connect();

        authenticate(s, board.id, impostor.getPrivate(), System.currentTimeMillis());

        assertThat(s.closedWith.getCode()).isEqualTo(4001);
        assertRejected(s, board.id);
    }

    @Test
    void unknownDeviceIsRejectedWithoutRevealingThatItIsUnknown() throws Exception {
        KeyPair kp = Ed25519TestUtil.generate();
        Session s = connect();

        authenticate(s, UUID.randomUUID(), kp.getPrivate(), System.currentTimeMillis());

        // Same 4001 as a bad signature: the close code must not be a device-id oracle.
        assertThat(s.closedWith.getCode()).isEqualTo(4001);
    }

    @Test
    void revokedDeviceIsRejected() throws Exception {
        Board board = enrolled();
        board.device.setRevokedAt(Instant.now().minusSeconds(60));
        Session s = connect();

        authenticate(s, board);

        assertThat(s.closedWith.getCode()).isEqualTo(4001);
        assertRejected(s, board.id);
    }

    @Test
    void deviceWithNoPublicKeyOnFileIsRejected() throws Exception {
        Board board = enrolled();
        board.device.setPublicKey(null);
        Session s = connect();

        authenticate(s, board);

        assertThat(s.closedWith.getCode()).isEqualTo(4001);
    }

    @Test
    void staleTimestampIsRejected() throws Exception {
        Board board = enrolled();
        Session s = connect();

        long tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000L);
        authenticate(s, board.id, board.keyPair.getPrivate(), tenMinutesAgo);

        assertThat(s.closedWith.getCode()).isEqualTo(4001);
    }

    /** A signature is bound to the session that issued the challenge; it does not travel. */
    @Test
    void aSignatureCapturedOnOneSessionDoesNotWorkOnAnother() throws Exception {
        Board board = enrolled();
        Session first = connect();
        Session second = connect();

        long now = System.currentTimeMillis();
        byte[] signed = BoardStreamAuthPayload.build(
                sessionIdOf(first), challengeOf(first), board.id, now);
        String signature = Base64.getEncoder()
                .encodeToString(Ed25519TestUtil.sign(board.keyPair.getPrivate(), signed));

        // Replayed against the second session, whose sessionId+challenge differ.
        deliver(second, authFrame(sessionIdOf(second), 1, board.id, now, signature));

        assertThat(second.closedWith.getCode()).isEqualTo(4001);
    }

    /** Domain separation: an agent-channel signature must not open the board stream. */
    @Test
    void anAgentChannelSignatureDoesNotAuthenticateOnTheBoardStream() throws Exception {
        Board board = enrolled();
        Session s = connect();

        long now = System.currentTimeMillis();
        byte[] agentPayload = AuthSignaturePayload.build(
                sessionIdOf(s), challengeOf(s), board.id, now);
        String signature = Base64.getEncoder()
                .encodeToString(Ed25519TestUtil.sign(board.keyPair.getPrivate(), agentPayload));

        deliver(s, authFrame(sessionIdOf(s), 1, board.id, now, signature));

        assertThat(s.closedWith.getCode()).isEqualTo(4001);
    }

    @Test
    void frameNamingSomeoneElsesSessionIdIsRejected() throws Exception {
        Board board = enrolled();
        Session s = connect();

        long now = System.currentTimeMillis();
        UUID wrongSession = UUID.randomUUID();
        byte[] signed = BoardStreamAuthPayload.build(wrongSession, challengeOf(s), board.id, now);
        String signature = Base64.getEncoder()
                .encodeToString(Ed25519TestUtil.sign(board.keyPair.getPrivate(), signed));

        deliver(s, authFrame(wrongSession, 1, board.id, now, signature));

        assertThat(s.closedWith.getCode()).isEqualTo(4001);
    }

    @Test
    void nonMonotonicSequenceNumberIsRejected() throws Exception {
        Board board = enrolled();
        Session s = connect();

        // The client's first frame must be seq 1; 0 is a regression against the initial 0.
        deliver(s, authFrame(sessionIdOf(s), 0, board.id, System.currentTimeMillis(), "x"));

        assertThat(s.closedWith.getCode()).isEqualTo(4002);
    }

    @Test
    void replayedSequenceNumberIsRejectedAfterAuth() throws Exception {
        Board board = enrolled();
        Session s = connect();
        authenticate(s, board); // consumes seq 1

        // Seq is checked before the frame is dispatched, so a replay closes on 4002 rather
        // than falling through to the bad-state path.
        deliver(s, authFrame(sessionIdOf(s), 1, board.id, System.currentTimeMillis(), "x"));

        assertThat(s.closedWith.getCode()).isEqualTo(4002);
    }

    @Test
    void anOversizedFrameIsRejectedWithoutBeingParsed() throws Exception {
        Session s = connect();

        deliver(s, "{\"type\":\"auth\",\"pad\":\"" + "A".repeat(8192) + "\"}");

        assertThat(s.closedWith.getCode()).isEqualTo(4004);
    }

    @Test
    void unparseableFrameIsRejected() throws Exception {
        Session s = connect();

        deliver(s, "{not json");

        assertThat(s.closedWith.getCode()).isEqualTo(4004);
    }

    /**
     * The subtype set is closed. A frame borrowed from the agent channel does not even
     * deserialize here, so it never reaches the phase machine.
     */
    @Test
    void aFrameTypeThisChannelDoesNotKnowIsRejected() throws Exception {
        Session s = connect();

        deliver(s, heartbeatShaped(sessionIdOf(s), 1));

        assertThat(s.closedWith.getCode()).isEqualTo(4004);
    }

    @Test
    void reAuthenticatingAfterAuthOkIsRejected() throws Exception {
        Board board = enrolled();
        Session s = connect();
        authenticate(s, board);

        long now = System.currentTimeMillis();
        byte[] signed = BoardStreamAuthPayload.build(sessionIdOf(s), challengeOf(s), board.id, now);
        String signature = Base64.getEncoder()
                .encodeToString(Ed25519TestUtil.sign(board.keyPair.getPrivate(), signed));
        deliver(s, authFrame(sessionIdOf(s), 2, board.id, now, signature));

        assertThat(s.closedWith.getCode()).isEqualTo(4003);
    }

    @Test
    void aConnectionThatNeverAuthenticatesIsReaped() throws Exception {
        ReflectionTestUtils.setField(handler, "authTimeoutMs", -1L); // deadline already past
        Session s = connect();

        handler.reapUnauthenticatedSessions();

        assertThat(s.closedWith.getCode()).isEqualTo(4008);
    }

    @Test
    void theReaperLeavesAuthenticatedBoardsAlone() throws Exception {
        ReflectionTestUtils.setField(handler, "authTimeoutMs", -1L);
        Board board = enrolled();
        Session s = connect();
        authenticate(s, board);

        handler.reapUnauthenticatedSessions();

        assertThat(s.closedWith).isNull();
        handler.contentChanged("events");
        assertThat(s.sent).hasSize(3);
    }

    // ── delivery ──────────────────────────────────────────────────────────

    @Test
    void contentPushReachesEveryAuthenticatedBoard() throws Exception {
        Session a = connectAuthenticated();
        Session b = connectAuthenticated();

        handler.contentChanged("posters");

        assertThat(refreshCount(a)).isEqualTo(1);
        assertThat(refreshCount(b)).isEqualTo(1);
    }

    @Test
    void configPushReachesOnlyTheTargetBoard() throws Exception {
        Board target = enrolled();
        Session mine = connect();
        authenticate(mine, target);
        Session theirs = connectAuthenticated();

        handler.configChanged(target.id);

        assertThat(refreshCount(mine)).isEqualTo(1);
        assertThat(refreshCount(theirs)).isZero();
    }

    @Test
    void messageIsJsonCarryingTypeReasonAndTarget() throws Exception {
        Board target = enrolled();
        Session s = connect();
        authenticate(s, target);

        handler.configChanged(target.id);

        JsonNode body = mapper.readTree(s.sent.get(2));
        assertThat(body.get("type").asText()).isEqualTo("refresh");
        assertThat(body.get("reason").asText()).isEqualTo("config");
        assertThat(body.get("deviceId").asText()).isEqualTo(target.id.toString());
        assertThat(body.hasNonNull("at")).isTrue();
    }

    /** The deployed kiosk substring-matches rather than parsing, so JSON must still hit it. */
    @Test
    void messageStillMatchesTheLegacyRefreshSubstring() throws Exception {
        Session s = connectAuthenticated();

        handler.refreshAll();

        assertThat(s.sent.get(s.sent.size() - 1).toUpperCase()).contains("REFRESH");
    }

    // ── session lifecycle (unchanged semantics) ───────────────────────────

    @Test
    void reconnectSupersedesThePriorSessionForTheSameDevice() throws Exception {
        Board board = enrolled();
        Session first = connect();
        authenticate(first, board);
        Session second = connect();
        authenticate(second, board);

        assertThat(first.closedWith).isNotNull();
        assertThat(first.closedWith.getCode()).isEqualTo(1000);
        assertThat(first.closedWith.getReason()).isEqualTo("superseded");

        handler.configChanged(board.id);
        assertThat(refreshCount(second)).isEqualTo(1);
        assertThat(refreshCount(first)).isZero();
    }

    /**
     * A superseded session's close callback arrives after its replacement is already
     * registered. It must not evict the newcomer.
     */
    @Test
    void lateCloseOfASupersededSessionLeavesTheReplacementRegistered() throws Exception {
        Board board = enrolled();
        Session first = connect();
        authenticate(first, board);
        Session second = connect();
        authenticate(second, board);

        handler.afterConnectionClosed(first.ws, CloseStatus.NORMAL);

        handler.configChanged(board.id);
        assertThat(refreshCount(second)).isEqualTo(1);
    }

    @Test
    void disconnectStopsDelivery() throws Exception {
        Session s = connectAuthenticated();
        handler.afterConnectionClosed(s.ws, CloseStatus.NORMAL);

        handler.contentChanged("events");

        assertThat(refreshCount(s)).isZero();
    }

    @Test
    void closedSessionsAreDroppedAndNeverWrittenTo() throws Exception {
        Session s = connectAuthenticated();
        s.open = false;

        handler.contentChanged("events");

        assertThat(refreshCount(s)).isZero();
    }

    /**
     * Tomcat's endpoint throws an unchecked {@link IllegalStateException} when a socket goes
     * bad between the open check and the write. One dying board must not abort the broadcast
     * for every board after it, nor fail the admin request that triggered the push.
     */
    @Test
    void aBoardThatThrowsOnWriteDoesNotStopTheBroadcast() throws Exception {
        Session broken = connectAuthenticated();
        Session healthy = connectAuthenticated();
        broken.throwOnSend = true;

        handler.contentChanged("posters");

        assertThat(refreshCount(healthy)).isEqualTo(1);
        assertThat(refreshCount(broken)).isZero();
    }

    /** Server-side seq is what a client validates; it must never go backwards. */
    @Test
    void outboundSeqIncreasesMonotonicallyAcrossPushes() throws Exception {
        Session s = connectAuthenticated();

        handler.contentChanged("posters");
        handler.contentChanged("events");
        handler.refreshAll();

        List<Long> seqs = new ArrayList<>();
        for (String json : s.sent) seqs.add(mapper.readTree(json).get("seq").asLong());
        assertThat(seqs).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void closeIfPresentDropsTheBoardAndStopsDelivery() throws Exception {
        Board board = enrolled();
        Session s = connect();
        authenticate(s, board);

        handler.closeIfPresent(board.id, CloseStatus.POLICY_VIOLATION.withReason("device_revoked"));

        assertThat(s.closedWith.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
        assertThat(s.closedWith.getReason()).isEqualTo("device_revoked");

        handler.contentChanged("events");
        assertThat(refreshCount(s)).isZero();
    }

    @Test
    void closeIfPresentIsANoOpForADeviceWithNoLiveSession() {
        handler.closeIfPresent(UUID.randomUUID(), CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void closeOfAConnectionThatNeverAuthenticatedIsHandled() throws Exception {
        Session s = connect();

        handler.afterConnectionClosed(s.ws, CloseStatus.NORMAL);

        // No device was ever bound, so there is nothing to deregister and nothing to throw.
        assertThat(s.sent).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** A device the repository will vouch for, with a keypair the test can sign as. */
    private record Board(UUID id, KeyPair keyPair, Device device) {}

    private Board enrolled() {
        UUID id = UUID.randomUUID();
        KeyPair kp = Ed25519TestUtil.generate();
        Device device = Device.builder()
                .id(id)
                .displayName("board")
                .publicKey(Ed25519TestUtil.rawPublicKey(kp.getPublic()))
                .build();
        when(deviceRepository.findById(id)).thenReturn(Optional.of(device));
        return new Board(id, kp, device);
    }

    private Session connect() throws Exception {
        return connectWithQuery(null);
    }

    private Session connectWithQuery(String query) throws Exception {
        Session session = new Session(query);
        handler.afterConnectionEstablished(session.ws);
        return session;
    }

    private Session connectAuthenticated() throws Exception {
        Session s = connect();
        authenticate(s, enrolled());
        return s;
    }

    private void authenticate(Session s, Board board) throws Exception {
        authenticate(s, board.id, board.keyPair.getPrivate(), System.currentTimeMillis());
    }

    private void authenticate(Session s, UUID deviceId, PrivateKey key, long timestamp) throws Exception {
        byte[] signed = BoardStreamAuthPayload.build(sessionIdOf(s), challengeOf(s), deviceId, timestamp);
        String signature = Base64.getEncoder().encodeToString(Ed25519TestUtil.sign(key, signed));
        deliver(s, authFrame(sessionIdOf(s), 1, deviceId, timestamp, signature));
    }

    /** Asserts nothing is delivered to a rejected session, including targeted pushes. */
    private void assertRejected(Session s, UUID deviceId) {
        handler.contentChanged("events");
        handler.configChanged(deviceId);
        assertThat(refreshCount(s)).isZero();
    }

    private long refreshCount(Session s) {
        return s.sent.stream().filter(json -> json.contains("\"type\":\"refresh\"")).count();
    }

    private UUID sessionIdOf(Session s) throws Exception {
        return UUID.fromString(mapper.readTree(s.sent.get(0)).get("sessionId").asText());
    }

    private String challengeOf(Session s) throws Exception {
        return mapper.readTree(s.sent.get(0)).get("challenge").asText();
    }

    private String authFrame(UUID sessionId, long seq, UUID deviceId, long timestamp, String signature)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "auth");
        body.put("seq", seq);
        body.put("sessionId", sessionId);
        body.put("deviceId", deviceId);
        body.put("timestamp", timestamp);
        body.put("signature", signature);
        return mapper.writeValueAsString(body);
    }

    /** A frame shaped like the agent channel's heartbeat — not a subtype this channel knows. */
    private String heartbeatShaped(UUID sessionId, long seq) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "heartbeat");
        body.put("seq", seq);
        body.put("sessionId", sessionId);
        return mapper.writeValueAsString(body);
    }

    private void deliver(Session s, String json) throws Exception {
        handler.handleMessage(s.ws, new TextMessage(json));
    }

    /** Minimal stand-in that records what the handler writes and how it closes. */
    private static class Session {
        final WebSocketSession ws = mock(WebSocketSession.class);
        final List<String> sent = new ArrayList<>();
        boolean open = true;
        /** Simulates the container throwing an unchecked error mid-write. */
        boolean throwOnSend = false;
        CloseStatus closedWith;

        Session(String query) throws Exception {
            when(ws.getId()).thenReturn(UUID.randomUUID().toString());
            when(ws.getUri()).thenReturn(URI.create(
                    "ws://board.local/api/refresh-musallahboard" + (query == null ? "" : "?" + query)));
            when(ws.isOpen()).thenAnswer(inv -> open);
            when(ws.getAttributes()).thenReturn(new HashMap<>());
            doAnswer(inv -> {
                if (throwOnSend) throw new IllegalStateException("endpoint in bad state");
                sent.add(((TextMessage) inv.getArgument(0)).getPayload());
                return null;
            }).when(ws).sendMessage(any());
            doAnswer(inv -> {
                closedWith = inv.getArgument(0);
                open = false;
                return null;
            }).when(ws).close(any(CloseStatus.class));
        }
    }
}
