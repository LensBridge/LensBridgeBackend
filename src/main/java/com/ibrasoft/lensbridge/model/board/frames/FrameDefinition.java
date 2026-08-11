package com.ibrasoft.lensbridge.model.board.frames;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrameDefinition {

    /**
     * Stable identity for this frame, independent of its position in the response.
     * Entity-backed, multi-instance types use {@code "<type>:<entityId>"} (e.g.
     * {@code "poster:3fa8..."}); singleton types (there is only ever one per assembly, e.g.
     * {@code daily_schedule} or {@code agenda}) use a fixed slug equal to the type name.
     * <p>
     * This is what a future per-frame ordering override will reference, so it must stay
     * stable across payload rebuilds even though {@code FrameDefinition} itself is
     * reconstructed from scratch on every request.
     */
    private String frameId;

    /**
     * The type of the frame.
     */
    private FrameType frameType;

    /**
     * How long the frame should be displayed in seconds.
     * null => auto
     */
    private Integer durationInSeconds;

    /**
     * Config object specific to the frame type.
     * This will likely be some subclass of FrameConfig.
     */
    private FrameConfig frameConfig;
}
