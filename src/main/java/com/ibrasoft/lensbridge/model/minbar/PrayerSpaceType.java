package com.ibrasoft.lensbridge.model.minbar;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/**
 * What kind of room this is. Deliberately not {@link Audience}, which the two were
 * conflated as before.
 * <p>
 * {@code Audience} answers "who is this listed for" — it is the filter the app applies
 * to a signed-in user, and a sister never sees a brothers' musallah. {@code
 * PrayerSpaceType} answers "what is it" — the label on the card, the colour of the pin,
 * and the chips someone taps to narrow the map. The two agree for a gendered musallah
 * and diverge for everything else: a multifaith room and a reflection bay are both shown
 * to everyone, but they are not the same place and a student looking for somewhere quiet
 * to pray between lectures wants to tell them apart.
 * <p>
 * {@link #defaultAudience()} is what makes carrying both cheap: a create request that
 * omits an audience gets the obvious one, so the only time anybody sets it by hand is the
 * genuinely unusual case — a reflection bay that a building has designated sisters-only,
 * say.
 */
public enum PrayerSpaceType {
    /** A dedicated brothers' musallah. */
    BROTHERS("brothers", Audience.BROTHERS),
    /** A dedicated sisters' musallah. */
    SISTERS("sisters", Audience.SISTERS),
    /** A room shared with other faith groups. Open to everyone, and not exclusively ours. */
    MULTIFAITH("multifaith", Audience.BOTH),
    /** A campus reflection bay: quiet, usually small, rarely equipped. */
    REFLECTION("reflection", Audience.BOTH);

    private final String typeName;
    private final Audience defaultAudience;

    PrayerSpaceType(String typeName, Audience defaultAudience) {
        this.typeName = typeName;
        this.defaultAudience = defaultAudience;
    }

    /** Who this kind of space is listed for when a request does not say. */
    public Audience defaultAudience() {
        return defaultAudience;
    }

    public static Optional<PrayerSpaceType> from(String s) {
        if (s == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(s) || t.typeName.equalsIgnoreCase(s))
                .findFirst();
    }

    @JsonValue
    public String toJson() {
        return typeName;
    }

    /** Accepts either case, matching {@link Audience#fromJson}. */
    @JsonCreator
    public static PrayerSpaceType fromJson(String s) {
        return from(s).orElseThrow(() -> new IllegalArgumentException("Unknown prayer space type: " + s));
    }

    @Override
    public String toString() {
        return typeName;
    }
}
