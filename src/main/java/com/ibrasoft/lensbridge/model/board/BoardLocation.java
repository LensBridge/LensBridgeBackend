package com.ibrasoft.lensbridge.model.board;

import java.util.Arrays;
import java.util.Optional;

public enum BoardLocation {
    BROTHERS_MUSALLAH("brothers", Audience.BROTHERS),
    SISTERS_MUSALLAH("sisters", Audience.SISTERS);

    private final String locationName;
    private final Audience audience;

    BoardLocation(String locationName, Audience audience) {
        this.locationName = locationName;
        this.audience = audience;
    }

    public Audience audience() { return audience; }

    public static Optional<BoardLocation> from(String s) {
        if (s == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(b -> b.name().equalsIgnoreCase(s) || b.toString().equalsIgnoreCase(s))
                .findFirst();
    }

    @Override
    public String toString() { return locationName; }
}
