package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.NextPrayerFrameConfig;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emits the NEXT_PRAYER frame. It carries no data — the countdown is computed client-side
 * from {@code deviceConfig.location} — but declaring it here gives it a real
 * {@code durationInSeconds} and a place in the ordered sequence instead of being synthesized
 * and hardcoded in the frontend.
 */
@Component
@Order(1)
public class NextPrayerFrameProducer implements FrameProducer {

    public static final String FRAME_ID = "next_prayer";

    @Override
    public List<FrameDefinition> produce(BoardContext ctx) {
        return List.of(FrameDefinition.builder()
                .frameId(FRAME_ID)
                .frameType(FrameType.NEXT_PRAYER)
                .durationInSeconds(configuredDuration(ctx))
                .frameConfig(new NextPrayerFrameConfig())
                .build());
    }

    /**
     * Never null, unlike the agenda's: this slide renders a countdown whose size does not
     * change with the data, so there is nothing for an "auto" mode to measure. A device with
     * no config at all still gets the default rather than handing the board a null to guess at.
     */
    private int configuredDuration(BoardContext ctx) {
        return ctx == null || ctx.getConfig() == null
                ? DeviceConfig.DEFAULT_NEXT_PRAYER_DURATION_SECONDS
                : ctx.getConfig().getNextPrayerDurationSeconds();
    }
}
