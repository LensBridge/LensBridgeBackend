package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.minbar.board.commands.CommandKind;
import com.ibrasoft.lensbridge.model.minbar.board.commands.CommandPayload;
import com.ibrasoft.lensbridge.model.minbar.board.commands.LogsTailPayload;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Binding and validation of command payloads on the issue path.
 *
 * <p>The dispatcher used to stringify {@code IssueCommandRequest.payload()} — a raw
 * {@code JsonNode} — and hand it to the device verbatim. The sealed {@link CommandPayload}
 * hierarchy was never deserialized on that path, so {@link LogsTailPayload}'s 1..500 bound and
 * every other constraint in it were dead code that merely looked authoritative. Every bound was
 * in practice delegated to agent code that does not live in this repository.
 *
 * <p>These tests pin the rule that the sealed hierarchy is now the single source of truth: what
 * gets stored and forwarded is the re-serialized validated object, not what the client sent.
 */
class CommandPayloadCodecTest {

    private ObjectMapper mapper;
    private CommandPayloadCodec codec;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        codec = new CommandPayloadCodec(mapper, Validation.buildDefaultValidatorFactory().getValidator());
    }

    private JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }

    @Test
    void acceptsAnInBoundsLogsTailPayload() throws Exception {
        CommandPayload payload = codec.parse(CommandKind.LOGS_TAIL, json("{\"lines\":200}"));

        assertThat(payload).isInstanceOf(LogsTailPayload.class);
        assertThat(((LogsTailPayload) payload).lines()).isEqualTo(200);
    }

    @Test
    void rejectsTheOutOfBoundsLineCountThatUsedToReachTheDevice() throws Exception {
        // The exact payload from the audit: accepted and forwarded verbatim before this change.
        assertThatThrownBy(() -> codec.parse(CommandKind.LOGS_TAIL, json("{\"lines\":999999999}")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("lines must be between 1 and 500");
    }

    @Test
    void rejectsBoundaryViolationsOnBothSides() throws Exception {
        assertThatThrownBy(() -> codec.parse(CommandKind.LOGS_TAIL, json("{\"lines\":0}")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> codec.parse(CommandKind.LOGS_TAIL, json("{\"lines\":501}")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(((LogsTailPayload) codec.parse(CommandKind.LOGS_TAIL, json("{\"lines\":1}"))).lines())
                .isEqualTo(1);
        assertThat(((LogsTailPayload) codec.parse(CommandKind.LOGS_TAIL, json("{\"lines\":500}"))).lines())
                .isEqualTo(500);
    }

    @Test
    void rejectsUnknownPropertiesRatherThanForwardingThem() throws Exception {
        // The shared ObjectMapper has FAIL_ON_UNKNOWN_PROPERTIES off, which is right for lenient
        // DTOs and exactly wrong for something bound for a physical device.
        assertThatThrownBy(() ->
                codec.parse(CommandKind.LOGS_TAIL, json("{\"lines\":10,\"anything\":\"else\"}")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsAPayloadWhoseDiscriminatorContradictsTheWireKind() throws Exception {
        assertThatThrownBy(() ->
                codec.parse(CommandKind.LOGS_TAIL, json("{\"kind\":\"system.reboot\"}")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void zeroArgCommandsAcceptAnAbsentOrEmptyPayload() throws Exception {
        assertThat(codec.parse(CommandKind.CHROME_RELOAD, null)).isNotNull();
        assertThat(codec.parse(CommandKind.SYSTEM_REBOOT, json("{}"))).isNotNull();
        assertThat(codec.parse(CommandKind.CHROME_SCREENSHOT, mapper.nullNode())).isNotNull();
    }

    @Test
    void rejectsANonObjectPayload() throws Exception {
        assertThatThrownBy(() -> codec.parse(CommandKind.LOGS_TAIL, json("\"lines=500\"")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> codec.parse(CommandKind.LOGS_TAIL, json("[1,2,3]")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
