package com.ibrasoft.lensbridge.model.board.frames;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for frame-specific configuration.
 * Uses Jackson annotations for polymorphic serialization.
 * <p>
 * {@link FrameType#NEXT_PRAYER} deliberately has no subtype here: the backend holds no
 * prayer-time source, so the board computes that frame itself from
 * {@code deviceConfig.location}.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PosterFrameConfig.class,        name = "poster"),
    @JsonSubTypes.Type(value = EventListFrameConfig.class,     name = "event_list"),
    @JsonSubTypes.Type(value = DailyScheduleFrameConfig.class, name = "daily_schedule"),
    @JsonSubTypes.Type(value = JummahFrameConfig.class,        name = "jummah"),
    @JsonSubTypes.Type(value = IslamicQuoteFrameConfig.class,  name = "islamic_quote")
})
public abstract class FrameConfig {

}
