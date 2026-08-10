package com.ibrasoft.lensbridge.model.board.frames;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Marker config for {@link FrameType#INSTAGRAM}. Carries no fields: the QR destination is
 * read client-side from {@code deviceConfig.socialUrl}, with its own app-level fallback when
 * that is unset. This class exists only so the frame has a real position and
 * {@code durationInSeconds} in the sequence, instead of being synthesized client-side.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InstagramFrameConfig extends FrameConfig {
}
