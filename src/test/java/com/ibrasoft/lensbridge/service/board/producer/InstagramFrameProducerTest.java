package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.InstagramFrameConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstagramFrameProducerTest {

    private final InstagramFrameProducer producer = new InstagramFrameProducer();

    @Test
    void produceReturnsOneMarkerFrameRegardlessOfContext() {
        List<FrameDefinition> frames = producer.produce(null);

        assertThat(frames).hasSize(1);
        FrameDefinition def = frames.get(0);
        assertThat(def.getFrameId()).isEqualTo("instagram");
        assertThat(def.getFrameType()).isEqualTo(FrameType.INSTAGRAM);
        assertThat(def.getDurationInSeconds()).isEqualTo(13);
        assertThat(def.getFrameConfig()).isInstanceOf(InstagramFrameConfig.class);
    }
}
