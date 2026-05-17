package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.frames.DailyScheduleFrameConfig;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameSlot;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DailyScheduleFrameTransformerTest {

    @InjectMocks
    private DailyScheduleFrameTransformer transformer;

    private BoardEvent event(String name) {
        return BoardEvent.builder()
                .name(name)
                .description("desc-" + name)
                .location("loc-" + name)
                .startTime(Instant.parse("2026-05-15T09:00:00Z"))
                .endTime(Instant.parse("2026-05-15T10:00:00Z"))
                .allDay(false)
                .audience(Audience.BOTH)
                .build();
    }

    @Test
    void supportsReturnsDailySchedule() {
        assertThat(transformer.supports()).isEqualTo(FrameType.DAILY_SCHEDULE);
    }

    @Test
    void transformProducesDailyScheduleFrameWithExpectedShell() {
        FrameDefinition def = transformer.transform(List.of(event("A")), null);

        assertThat(def.getFrameType()).isEqualTo(FrameType.DAILY_SCHEDULE);
        assertThat(def.getSlot()).isEqualTo(FrameSlot.PRIMARY);
        assertThat(def.getDurationInSeconds()).isNull();
        assertThat(def.getPriority()).isNull();
        assertThat(def.getFrameConfig()).isInstanceOf(DailyScheduleFrameConfig.class);
    }

    @Test
    void transformMapsEventFieldsIntoEventViews() {
        BoardEvent e = event("Lecture");

        FrameDefinition def = transformer.transform(List.of(e), null);

        DailyScheduleFrameConfig config = (DailyScheduleFrameConfig) def.getFrameConfig();
        assertThat(config.getHeading()).isEqualTo("Today");
        assertThat(config.getEvents()).hasSize(1);
        EventView view = config.getEvents().get(0);
        assertThat(view.getName()).isEqualTo("Lecture");
        assertThat(view.getDescription()).isEqualTo("desc-Lecture");
        assertThat(view.getLocation()).isEqualTo("loc-Lecture");
        assertThat(view.getStartTime()).isEqualTo(Instant.parse("2026-05-15T09:00:00Z"));
        assertThat(view.getEndTime()).isEqualTo(Instant.parse("2026-05-15T10:00:00Z"));
        assertThat(view.getAllDay()).isFalse();
    }

    @Test
    void transformPreservesEventOrderAndCount() {
        FrameDefinition def = transformer.transform(List.of(event("A"), event("B"), event("C")), null);

        DailyScheduleFrameConfig config = (DailyScheduleFrameConfig) def.getFrameConfig();
        assertThat(config.getEvents())
                .extracting(EventView::getName)
                .containsExactly("A", "B", "C");
    }

    @Test
    void transformWithEmptyListProducesEmptyEventsButValidFrame() {
        FrameDefinition def = transformer.transform(Collections.emptyList(), null);

        DailyScheduleFrameConfig config = (DailyScheduleFrameConfig) def.getFrameConfig();
        assertThat(config.getHeading()).isEqualTo("Today");
        assertThat(config.getEvents()).isEmpty();
        assertThat(def.getFrameType()).isEqualTo(FrameType.DAILY_SCHEDULE);
    }
}
