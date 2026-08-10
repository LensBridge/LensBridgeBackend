package com.ibrasoft.lensbridge.model.board.frames;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for frame-specific configuration.
 * Uses Jackson annotations for polymorphic serialization.
 * <p>
 * {@link FrameType#NEXT_PRAYER} uses empty marker configs
 * ({@link NextPrayerFrameConfig}, {@link InstagramFrameConfig}) — the backend has no data to
 * contribute to either (prayer times and the QR destination are both resolved client-side), but
 * the frame still needs a real position and {@code durationInSeconds} in the sequence rather
 * than being synthesized outside the API contract.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PosterFrameConfig.class,                    name = "poster"),
    @JsonSubTypes.Type(value = JummahFrameConfig.class,                    name = "jummah"),
    @JsonSubTypes.Type(value = IslamicQuoteFrameConfig.class,              name = "islamic_quote"),
    @JsonSubTypes.Type(value = NextPrayerFrameConfig.class,                name = "next_prayer"),
    @JsonSubTypes.Type(value = AgendaFrameConfig.class,                    name = "agenda"),
    @JsonSubTypes.Type(value = PromotableSocialMediaFrameConfig.class,     name = "socials")
})
public abstract class FrameConfig {

}
