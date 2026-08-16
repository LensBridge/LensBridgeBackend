package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.minbar.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.minbar.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.minbar.board.frames.NextPrayerFrameConfig;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.model.minbar.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.service.board.BoardContext;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NextPrayerFrameProducerTest {

    private final NextPrayerFrameProducer producer = new NextPrayerFrameProducer();

    @Test
    void produceReturnsOneMarkerFrame() {
        List<FrameDefinition> frames = producer.produce(null);

        assertThat(frames).hasSize(1);
        FrameDefinition def = frames.get(0);
        assertThat(def.getFrameId()).isEqualTo("next_prayer");
        assertThat(def.getFrameType()).isEqualTo(FrameType.NEXT_PRAYER);
        assertThat(def.getDurationInSeconds()).isEqualTo(12);
        assertThat(def.getFrameConfig()).isInstanceOf(NextPrayerFrameConfig.class);
    }

    private BoardContext ctxWith(DeviceConfig config) {
        return BoardContext.builder()
                .device(Device.builder().audience(Audience.BOTH).build())
                .config(config)
                .now(ZonedDateTime.of(2026, 5, 13, 10, 0, 0, 0, ZoneId.of("America/Toronto")))
                .build();
    }

    @Test
    void usesTheDurationConfiguredForTheDevice() {
        DeviceConfig config = DeviceConfig.builder().nextPrayerDurationSeconds(25).build();

        assertThat(producer.produce(ctxWith(config)).get(0).getDurationInSeconds()).isEqualTo(25);
    }

    /** A config row predating the column has null stored; the frame still needs a number. */
    @Test
    void fallsBackToTheDefaultWhenNothingIsStored() {
        DeviceConfig config = DeviceConfig.builder().build();

        assertThat(producer.produce(ctxWith(config)).get(0).getDurationInSeconds())
                .isEqualTo(DeviceConfig.DEFAULT_NEXT_PRAYER_DURATION_SECONDS);
    }

    /** Unlike the agenda, this frame must never hand the board a null to interpret. */
    @Test
    void neverEmitsNull() {
        assertThat(producer.produce(ctxWith(null)).get(0).getDurationInSeconds()).isNotNull();
        assertThat(producer.produce(null).get(0).getDurationInSeconds()).isNotNull();
    }
}
