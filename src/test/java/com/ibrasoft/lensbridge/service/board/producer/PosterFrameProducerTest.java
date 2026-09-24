package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.PosterFrameConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosterFrameProducerTest {

    @Mock
    private PosterService posterService;

    @InjectMocks
    private PosterFrameProducer transformer;

    private Poster poster(String title, String image, int duration) {
        return Poster.builder()
                .id(UUID.randomUUID())
                .title(title)
                .image(image)
                .duration(duration)
                .startTime(Instant.parse("2026-05-15T00:00:00Z"))
                .endTime(Instant.parse("2026-05-20T00:00:00Z"))
                .audience(Audience.BOTH)
                .build();
    }

    @Test
    void transformMapsPosterFieldsAndDuration() {
        Poster p = poster("Ramadan", "https://img/r.png", 15);
        FrameDefinition def = transformer.transform(p, null);

        assertThat(def.getFrameId()).isEqualTo("poster:" + p.getId());
        assertThat(def.getFrameType()).isEqualTo(FrameType.POSTER);
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

    // ==================== Which posters ====================

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    private static Device device() {
        return Device.builder().id(UUID.randomUUID()).displayName("d").audience(Audience.SISTERS).build();
    }

    /** The online board's behaviour is unchanged: posters active at this instant, nothing wider. */
    @Test
    void liveContextAsksForPostersActiveRightNow() {
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 13, 15, 0, 0, 0, TORONTO);
        BoardContext ctx = BoardContext.builder().device(device()).now(now).build();
        Poster p = poster("Now", "u", 10);
        when(posterService.getActivePosterFramesForAudience(Audience.SISTERS)).thenReturn(List.of(p));

        assertThat(transformer.produce(ctx)).extracting(FrameDefinition::getFrameId)
                .containsExactly("poster:" + p.getId());
        verify(posterService, never()).getPostersForAudienceOverlapping(any(), any(), any());
    }

    /** An offline bundle day: anything active at any point between this midnight and the next. */
    @Test
    void wholeDayContextAsksForPostersOverlappingTheDay() {
        BoardContext ctx = BoardContext.of(device(), TORONTO, LocalDate.of(2026, 5, 13));
        Poster p = poster("Today", "u", 10);
        when(posterService.getPostersForAudienceOverlapping(Audience.SISTERS,
                Instant.parse("2026-05-13T04:00:00Z"), Instant.parse("2026-05-14T04:00:00Z")))
                .thenReturn(List.of(p));

        assertThat(transformer.produce(ctx)).extracting(FrameDefinition::getFrameId)
                .containsExactly("poster:" + p.getId());
        verify(posterService, never()).getActivePosterFramesForAudience(any());
    }
}
