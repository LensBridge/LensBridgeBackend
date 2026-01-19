package com.ibrasoft.lensbridge.model.board.frames;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for frame-specific configuration.
 * Uses Jackson annotations for polymorphic serialization.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PosterFrameConfig.class, name = "poster")
})
public abstract class FrameConfig {

}
