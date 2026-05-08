package com.ibrasoft.lensbridge.model.board;

import java.util.Arrays;
import java.util.Optional;

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

    @Override
    public String toString() {
        return audienceName;
    }
}
