package com.ibrasoft.lensbridge.model.board.frames;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Marker config for {@link FrameType#NEXT_PRAYER}. Carries no fields: the backend holds no
 * prayer-time source, so the board computes the countdown itself from
 * {@code deviceConfig.location}. This class exists only so the frame has a real position and
 * {@code durationInSeconds} in the sequence, instead of being synthesized client-side.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NextPrayerFrameConfig extends FrameConfig {
}
