package com.ibrasoft.lensbridge.model.board.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandPayloadsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    // ── kinds without a payload ───────────────────────────────────────────

    /** The admin console sends {} for every button; absent and null mean the same. */
    @ParameterizedTest
    @EnumSource(value = CommandKind.class, names = "LOGS_TAIL", mode = EnumSource.Mode.EXCLUDE)
    void parameterlessKindsAcceptNoPayloadAndStoreNull(CommandKind kind) {
        assertThat(kind.validatePayload(null).isNull()).isTrue();
        assertThat(kind.validatePayload(json("null")).isNull()).isTrue();
        assertThat(kind.validatePayload(json("{}")).isNull()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = CommandKind.class, names = "LOGS_TAIL", mode = EnumSource.Mode.EXCLUDE)
    void parameterlessKindsRefuseAnyPayload(CommandKind kind) {
        for (String payload : new String[]{"{\"url\":\"https://evil.example\"}", "[]", "[1]", "\"x\"", "1", "true"}) {
            assertThatThrownBy(() -> kind.validatePayload(json(payload)))
                    .as(payload)
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessage(kind.getWireName() + " takes no payload");
        }
    }

    // ── logs.tail ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {1, 100, 500})
    void logsTailAcceptsTheAgentsRange(int lines) {
        assertThat(CommandKind.LOGS_TAIL.validatePayload(json("{\"lines\":" + lines + "}")))
                .isEqualTo(json("{\"lines\":" + lines + "}"));
    }

    /** No lines means the agent's default of 100; nothing is invented here. */
    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"lines\":null}"})
    void logsTailWithoutLinesLeavesTheDefaultToTheAgent(String payload) {
        assertThat(CommandKind.LOGS_TAIL.validatePayload(json(payload)).isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"lines\":0}", "{\"lines\":-1}", "{\"lines\":501}", "{\"lines\":999999999}",
            "{\"lines\":99999999999999999999}", "{\"lines\":2.5}", "{\"lines\":100.0}", "{\"lines\":\"100\"}",
            "{\"lines\":true}", "{\"lines\":[100]}"})
    void logsTailRefusesLinesOutsideOneToFiveHundred(String payload) {
        assertThatThrownBy(() -> CommandKind.LOGS_TAIL.validatePayload(json(payload)))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessage("logs.tail lines must be a whole number from 1 to 500");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"lines\":100,\"unit\":\"sshd\"}", "{\"since\":\"1h\"}"})
    void logsTailRefusesUnknownFields(String payload) {
        assertThatThrownBy(() -> CommandKind.LOGS_TAIL.validatePayload(json(payload)))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("unknown field");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "100", "\"lines\""})
    void logsTailRefusesANonObject(String payload) {
        assertThatThrownBy(() -> CommandKind.LOGS_TAIL.validatePayload(json(payload)))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("must be an object");
    }

    /** The agent bounds (internal/commands/logs_tail.go) and these must stay the same. */
    @org.junit.jupiter.api.Test
    void logsTailBoundsMatchTheAgent() {
        assertThat(CommandPayloads.LOGS_TAIL_MIN_LINES).isEqualTo(1);
        assertThat(CommandPayloads.LOGS_TAIL_MAX_LINES).isEqualTo(500);
    }
}
