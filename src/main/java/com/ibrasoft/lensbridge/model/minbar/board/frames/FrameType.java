package com.ibrasoft.lensbridge.model.minbar.board.frames;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ibrasoft.lensbridge.model.minbar.Audience;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kind of frame the assembler emitted, as it appears in
 * {@code FrameDefinition.frameType}.
 * <p>
 * {@code @JsonValue} is crucial, else Jackson will serialize the enum name instead of the wire 
 * string, which makes things like {@code POSTER} appear in the database and on the wire instead of 
 * {@code poster}. 
 * <p>
 * Mirrors {@link Audience}, which had the same
 * shape and got this right.
 */
public enum FrameType {
    POSTER("poster"),
    NEXT_PRAYER("next_prayer"),
    AGENDA("agenda"),
    JUMMAH("jummah"),
    ISLAMIC_QUOTE("islamic_quote"),
    SOCIALS("socials");

    private final String typeName;
    FrameType(String typeName) {
        this.typeName = typeName;
    }

    public static Optional<FrameType> from(String s) {
        if (s == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(s) || t.typeName.equalsIgnoreCase(s))
                .findFirst();
    }

    @JsonValue
    public String toJson() {
        return typeName;
    }

    /** Accepts either spelling, so anything persisted or sent as {@code POSTER} still binds. */
    @JsonCreator
    public static FrameType fromJson(String s) {
        return from(s).orElseThrow(() -> new IllegalArgumentException("Unknown frame type: " + s));
    }

    @Override
    public String toString() {
        return typeName;
    }
}
