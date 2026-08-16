package com.ibrasoft.lensbridge.model.minbar;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

// TECH DEBT:
// Eventually I would like to have tenancy-defined audiences and allow audience meshing rather than a binary
// brothers/sisters/everyone (i.e: more than 3 audiences)
// This is sufficient for a version 1 prototype, though

public enum Audience {
    BROTHERS("brothers"),
    SISTERS("sisters"),
    BOTH("both");

    private final String audienceName;

    Audience(String audienceName) {
        this.audienceName = audienceName;
    }

    public static Optional<Audience> from(String s) {
        if (s == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(a -> a.name().equalsIgnoreCase(s) || a.toString().equalsIgnoreCase(s))
                .findFirst();
    }

    @JsonValue
    public String toJson() {
        return audienceName;
    }

    /** Accepts either case, so pre-existing callers sending "BROTHERS" still bind. */
    @JsonCreator
    public static Audience fromJson(String s) {
        return from(s).orElseThrow(() -> new IllegalArgumentException("Unknown audience: " + s));
    }

    @Override
    public String toString() {
        return audienceName;
    }
}
