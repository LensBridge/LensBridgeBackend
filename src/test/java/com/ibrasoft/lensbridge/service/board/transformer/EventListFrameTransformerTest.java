package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.frames.EventListFrameConfig;
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
class EventListFrameTransformerTest {

    @InjectMocks
    private EventListFrameTransformer transformer;

    private BoardEvent event(String name, boolean allDay) {
        return BoardEvent.builder()
                .name(name)
                .description("desc-" + name)
                .location("loc-" + name)
                .startTime(Instant.parse("2026-05-12T00:00:00Z"))
                .endTime(Instant.parse("2026-05-12T23:59:59Z"))
                .allDay(allDay)
                .audience(Audience.BROTHERS)
                .build();
    }

    @Test
    void supportsReturnsEventList() {
        assertThat(transformer.supports()).isEqualTo(FrameType.EVENT_LIST);
    }

    @Test
    void transformProducesEventListFrameWithExpectedShell() {
        FrameDefinition def = transformer.transform(List.of(event("A", false)), null);

        assertThat(def.getFrameType()).isEqualTo(FrameType.EVENT_LIST);
        assertThat(def.getSlot()).isEqualTo(FrameSlot.PRIMARY);
        assertThat(def.getDurationInSeconds()).isNull();
        assertThat(def.getPriority()).isNull();
        assertThat(def.getFrameConfig()).isInstanceOf(EventListFrameConfig.class);
    }

    @Test
    void transformMapsEventFieldsIncludingAllDay() {
        FrameDefinition def = transformer.transform(List.of(event("Conference", true)), null);

        EventListFrameConfig config = (EventListFrameConfig) def.getFrameConfig();
        assertThat(config.getHeading()).isEqualTo("This Week");
        assertThat(config.getEvents()).hasSize(1);
        EventView view = config.getEvents().get(0);
        assertThat(view.getName()).isEqualTo("Conference");
        assertThat(view.getDescription()).isEqualTo("desc-Conference");
        assertThat(view.getLocation()).isEqualTo("loc-Conference");
        assertThat(view.getStartTime()).isEqualTo(Instant.parse("2026-05-12T00:00:00Z"));
        assertThat(view.getEndTime()).isEqualTo(Instant.parse("2026-05-12T23:59:59Z"));
        assertThat(view.getAllDay()).isTrue();
    }

    @Test
    void transformPreservesEventOrderAndCount() {
        FrameDefinition def = transformer.transform(
                List.of(event("A", false), event("B", false)), null);

        EventListFrameConfig config = (EventListFrameConfig) def.getFrameConfig();
        assertThat(config.getEvents())
                .extracting(EventView::getName)
                .containsExactly("A", "B");
    }

    @Test
    void transformWithEmptyListProducesEmptyEventsButValidFrame() {
        FrameDefinition def = transformer.transform(Collections.emptyList(), null);

        EventListFrameConfig config = (EventListFrameConfig) def.getFrameConfig();
        assertThat(config.getHeading()).isEqualTo("This Week");
        assertThat(config.getEvents()).isEmpty();
        assertThat(def.getFrameType()).isEqualTo(FrameType.EVENT_LIST);
    }
}
