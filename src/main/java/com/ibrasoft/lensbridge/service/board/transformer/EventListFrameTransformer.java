package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.dto.response.frames.EventView;
import com.ibrasoft.lensbridge.model.board.Event;
import com.ibrasoft.lensbridge.model.board.frames.EventListFrameConfig;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameSlot;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Transforms a list of Events into a single EVENT_LIST FrameDefinition
 * representing events for the current week.
 */
@Component
public class EventListFrameTransformer implements FrameTransformer<List<Event>> {

    @Override
    public FrameType supports() {
        return FrameType.EVENT_LIST;
    }

    @Override
    public FrameDefinition transform(List<Event> events, BoardContext ctx) {
        List<EventView> eventViews = events.stream()
                .map(this::toEventView)
                .collect(Collectors.toList());

        EventListFrameConfig config = EventListFrameConfig.builder()
                .heading("This Week")
                .events(eventViews)
                .build();

        return FrameDefinition.builder()
                .frameType(FrameType.EVENT_LIST)
                .durationInSeconds(null)
                .frameConfig(config)
                .slot(FrameSlot.PRIMARY)
                .priority(null)
                .build();
    }

    private EventView toEventView(Event event) {
        return EventView.builder()
                .name(event.getName())
                .description(event.getDescription())
                .location(event.getLocation())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .allDay(event.getAllDay())
                .build();
    }
}
