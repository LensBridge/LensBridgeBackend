package com.ibrasoft.lensbridge.model.board.frames;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FrameDefinition {
    
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
