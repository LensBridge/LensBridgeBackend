package com.ibrasoft.lensbridge.model.board.frames;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Base class for frame-specific configuration.
 * Uses Jackson annotations for polymorphic serialization.
 * <p>
 * {@link FrameType#NEXT_PRAYER} uses empty marker configs
 * ({@link NextPrayerFrameConfig}) — the backend has no data to
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
@Schema(
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "poster",         schema = PosterFrameConfig.class),
        @DiscriminatorMapping(value = "jummah",         schema = JummahFrameConfig.class),
        @DiscriminatorMapping(value = "islamic_quote",  schema = IslamicQuoteFrameConfig.class),
        @DiscriminatorMapping(value = "next_prayer",    schema = NextPrayerFrameConfig.class),
        @DiscriminatorMapping(value = "agenda",         schema = AgendaFrameConfig.class),
        @DiscriminatorMapping(value = "socials",        schema = PromotableSocialMediaFrameConfig.class)
    }
)
public abstract class FrameConfig {

}
