package com.ibrasoft.lensbridge.model.board.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;

import java.util.Iterator;

/**
 * Payload rules per command kind, checked when a command is issued. What passes is
 * re-serialized from the checked values, so nothing the client tacked on reaches a board.
 * <p>
 * The agent (internal/commands in the agent repo) still validates on its side; these rules
 * mirror it, so an admin gets a 400 now instead of a "rejected" result from the board later,
 * and a payload field added there without a rule here is refused rather than passed through.
 */
public final class CommandPayloads {

    /** {@code logs.tail} bounds, as the agent's internal/commands/logs_tail.go enforces them. */
    public static final int LOGS_TAIL_MIN_LINES = 1;
    public static final int LOGS_TAIL_MAX_LINES = 500;

    private CommandPayloads() {
    }

    /** Checks one kind's payload and returns what to store and send. */
    @FunctionalInterface
    interface Rule {
        JsonNode check(CommandKind kind, JsonNode payload);
    }

    /**
     * No payload. Absent, JSON null and an empty object (what the admin console sends for a
     * parameterless command) all mean none; anything else is refused rather than ignored.
     */
    static JsonNode none(CommandKind kind, JsonNode payload) {
        if (isEmpty(payload)) return NullNode.getInstance();
        throw new InvalidCommandException(kind.getWireName() + " takes no payload");
    }

    /**
     * {@code {"lines": n}} with n a whole number from 1 to 500, or no payload (or no
     * {@code lines}) for the agent's default of 100.
     */
    static JsonNode logsTail(CommandKind kind, JsonNode payload) {
        if (isEmpty(payload)) return NullNode.getInstance();
        String bounds = "a whole number from " + LOGS_TAIL_MIN_LINES + " to " + LOGS_TAIL_MAX_LINES;
        if (!payload.isObject()) {
            throw new InvalidCommandException(kind.getWireName()
                    + " payload must be an object like {\"lines\": 100}");
        }
        for (Iterator<String> names = payload.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!"lines".equals(name)) {
                throw new InvalidCommandException(kind.getWireName() + " payload has an unknown field \""
                        + truncate(name) + "\"; only \"lines\" is accepted");
            }
        }
        JsonNode lines = payload.get("lines");
        if (lines == null || lines.isNull()) return NullNode.getInstance();
        if (!lines.isIntegralNumber() || !lines.canConvertToInt()
                || lines.intValue() < LOGS_TAIL_MIN_LINES || lines.intValue() > LOGS_TAIL_MAX_LINES) {
            throw new InvalidCommandException(kind.getWireName() + " lines must be " + bounds);
        }
        return JsonNodeFactory.instance.objectNode().put("lines", lines.intValue());
    }

    static boolean isEmpty(JsonNode payload) {
        return payload == null || payload.isNull() || payload.isMissingNode()
                || (payload.isObject() && payload.isEmpty());
    }

    private static String truncate(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }
}
