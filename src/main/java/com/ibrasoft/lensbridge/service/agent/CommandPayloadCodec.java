package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibrasoft.lensbridge.model.minbar.board.commands.CommandKind;
import com.ibrasoft.lensbridge.model.minbar.board.commands.CommandPayload;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns the raw {@code payload} JSON of an issue request into a validated
 * {@link CommandPayload}, and back into the JSON that is persisted and pushed to the device.
 * <p>
 * This exists because the issue path used to stringify whatever JSON the operator sent and
 * hand it to the kiosk agent verbatim: the sealed {@code CommandPayload} hierarchy — bounds
 * and all — was never on that path, so its constraints only <em>looked</em> authoritative.
 * Everything a payload can express now has to survive four gates before it leaves the server:
 * <ol>
 *   <li>the payload must be a JSON object (or absent);</li>
 *   <li>any {@code kind} inside the payload must agree with the request's {@code kind} —
 *       an operator cannot smuggle a screenshot payload into a reload command;</li>
 *   <li>it must deserialize into the record {@link CommandKind#getPayloadType()} names,
 *       with unknown properties rejected rather than silently forwarded;</li>
 *   <li>it must pass Jakarta Bean Validation.</li>
 * </ol>
 * Only the re-serialized, validated object is stored, so the device never sees a field the
 * server did not understand.
 * <p>
 * The discriminator is stripped on the way out: the frame already carries {@code kind} at
 * top level, and keeping the stored payload free of it preserves the shape the agent has
 * always been handed.
 */
@Component
@Slf4j
public class CommandPayloadCodec {

    /** Discriminator property configured on {@link CommandPayload}'s {@code @JsonTypeInfo}. */
    private static final String KIND_PROPERTY = "kind";

    private final ObjectMapper objectMapper;
    private final ObjectReader strictReader;
    private final Validator validator;

    public CommandPayloadCodec(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        // A dedicated reader: the shared mapper has FAIL_ON_UNKNOWN_PROPERTIES off, which is
        // the right default for lenient DTOs and exactly wrong for a payload bound for a device.
        this.strictReader = objectMapper
                .reader()
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .forType(CommandPayload.class);
    }

    /**
     * Parses and validates {@code raw} as the payload for {@code kind}.
     *
     * @throws ResponseStatusException 400 with a message safe to hand back to the caller
     */
    public CommandPayload parse(CommandKind kind, JsonNode raw) {
        ObjectNode body = asObject(kind, raw);
        checkDiscriminator(kind, body);
        body.put(KIND_PROPERTY, kind.getWireName());

        CommandPayload payload;
        try {
            payload = strictReader.readValue(body);
        } catch (Exception e) {
            log.warn("Rejected {} payload: {}", kind.getWireName(), e.getMessage());
            throw badRequest("Invalid payload for command " + kind.getWireName() + ": " + rootCauseMessage(e));
        }

        // Belt and braces: the Jackson registry and CommandKind are pinned together by
        // CommandKindTest, so a mismatch here means one of them was edited without the other.
        if (!kind.getPayloadType().isInstance(payload)) {
            log.error("Payload for {} deserialized to {}, expected {}",
                    kind.getWireName(), payload.getClass().getSimpleName(), kind.getPayloadType().getSimpleName());
            throw badRequest("Payload does not match command kind " + kind.getWireName());
        }

        Set<ConstraintViolation<CommandPayload>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            log.warn("Rejected {} payload: {}", kind.getWireName(), details);
            throw badRequest("Invalid payload for command " + kind.getWireName() + ": " + details);
        }
        return payload;
    }

    /**
     * Serializes a validated payload for storage and delivery, without the {@code kind}
     * discriminator (the command row and the outgoing frame both carry it already).
     */
    public String toStoredJson(CommandPayload payload) {
        ObjectNode node = objectMapper.valueToTree(payload);
        node.remove(KIND_PROPERTY);
        return node.toString();
    }

    /** Convenience for the issue path: validate, then render what gets stored. */
    public String validateAndSerialize(CommandKind kind, JsonNode raw) {
        return toStoredJson(parse(kind, raw));
    }

    private ObjectNode asObject(CommandKind kind, JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        if (!raw.isObject()) {
            throw badRequest("Payload for command " + kind.getWireName() + " must be a JSON object");
        }
        return ((ObjectNode) raw).deepCopy();
    }

    private void checkDiscriminator(CommandKind kind, ObjectNode body) {
        JsonNode declared = body.get(KIND_PROPERTY);
        if (declared == null || declared.isNull()) {
            return;
        }
        if (!declared.isTextual() || !kind.getWireName().equals(declared.asText())) {
            log.warn("Rejected command {}: payload declares kind {}", kind.getWireName(), declared);
            throw badRequest("Payload kind does not match command kind " + kind.getWireName());
        }
    }

    /**
     * Jackson nests the useful part of a parse failure inside wrapper exceptions and appends
     * the parser location, which leaks nothing sensitive but is noise to an API caller.
     */
    private static String rootCauseMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return "unreadable payload";
        }
        int at = message.indexOf("\n at [");
        if (at >= 0) {
            message = message.substring(0, at);
        }
        return message.strip();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** Kept for readability of the violation ordering above. */
    @SuppressWarnings("unused")
    private static final Comparator<String> NATURAL = Comparator.naturalOrder();
}
