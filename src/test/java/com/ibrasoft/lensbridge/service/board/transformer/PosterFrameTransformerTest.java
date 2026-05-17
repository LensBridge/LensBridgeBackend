package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameSlot;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.PosterFrameConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PosterFrameTransformerTest {

    @InjectMocks
    private PosterFrameTransformer transformer;

    private Poster poster(String title, String image, int duration) {
        return Poster.builder()
                .title(title)
                .image(image)
                .duration(duration)
                .startTime(Instant.parse("2026-05-15T00:00:00Z"))
                .endTime(Instant.parse("2026-05-20T00:00:00Z"))
                .audience(Audience.BOTH)
                .build();
    }

    @Test
    void supportsReturnsPoster() {
        assertThat(transformer.supports()).isEqualTo(FrameType.POSTER);
    }

    @Test
    void transformMapsPosterFieldsAndDuration() {
        FrameDefinition def = transformer.transform(poster("Ramadan", "https://img/r.png", 15), null);

        assertThat(def.getFrameType()).isEqualTo(FrameType.POSTER);
        assertThat(def.getSlot()).isEqualTo(FrameSlot.PRIMARY);
        assertThat(def.getPriority()).isNull();
        assertThat(def.getDurationInSeconds()).isEqualTo(15);
        assertThat(def.getFrameConfig()).isInstanceOf(PosterFrameConfig.class);

        PosterFrameConfig config = (PosterFrameConfig) def.getFrameConfig();
        assertThat(config.getPosterUrl()).isEqualTo("https://img/r.png");
        assertThat(config.getTitle()).isEqualTo("Ramadan");
    }

    @Test
    void transformWithZeroDurationStillSetsDuration() {
        FrameDefinition def = transformer.transform(poster("Z", "u", 0), null);

        assertThat(def.getDurationInSeconds()).isEqualTo(0);
    }

    @Test
    void transformWithNullTitleAndImageProducesNullConfigFields() {
        FrameDefinition def = transformer.transform(poster(null, null, 10), null);

        PosterFrameConfig config = (PosterFrameConfig) def.getFrameConfig();
        assertThat(config.getPosterUrl()).isNull();
        assertThat(config.getTitle()).isNull();
        assertThat(def.getDurationInSeconds()).isEqualTo(10);
    }
}
