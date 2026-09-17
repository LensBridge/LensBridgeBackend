package com.ibrasoft.lensbridge.model.board;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/**
 * The platform a {@link PromotableSocialMedia} entry points at, as it appears in
 * {@code PromotableSocialMediaFrameConfig.socialType}.
 * <p>
 * {@code @JsonValue} is crucial, else Jackson serializes the enum name and the wire carries
 * {@code INSTAGRAM} where the frontend — which keys its platform icons and brand colours off
 * the lowercase label — expects {@code instagram}.
 * <p>
 * Mirrors {@link Audience} and {@link com.ibrasoft.lensbridge.model.board.frames.FrameType},
 * which had the same shape and got this right.
 * <p>
 * This affects the wire only. The column is {@code @Enumerated(EnumType.STRING)}, so the
 * database still stores {@code name()}.
 */
public enum SocialType {
    INSTAGRAM("instagram"),
    YOUTUBE("youtube"),
    TIKTOK("tiktok"),
    WHATSAPP("whatsapp"),
    OTHER("other");

    private final String label;

    SocialType(String label) {
        this.label = label;
    }

    public static Optional<SocialType> from(String s) {
        if (s == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(s) || t.label.equalsIgnoreCase(s))
                .findFirst();
    }

    @JsonValue
    public String toJson() {
        return label;
    }

    /** Accepts either spelling, so anything persisted or sent as {@code INSTAGRAM} still binds. */
    @JsonCreator
    public static SocialType fromJson(String s) {
        return from(s).orElseThrow(() -> new IllegalArgumentException("Unknown social type: " + s));
    }

    @Override
    public String toString() {
        return label;
    }
}
