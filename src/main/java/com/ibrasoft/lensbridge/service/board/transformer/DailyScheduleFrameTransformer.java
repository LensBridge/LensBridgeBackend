package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import com.ibrasoft.lensbridge.model.board.Event;
import com.ibrasoft.lensbridge.model.board.frames.DailyScheduleFrameConfig;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameSlot;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Transforms a list of Events into a single DAILY_SCHEDULE FrameDefinition
 * representing events for the current day.
 */
@Component
public class DailyScheduleFrameTransformer implements FrameTransformer<List<Event>> {

    @Override
    public FrameType supports() {
        return FrameType.DAILY_SCHEDULE;
    }

    @Override
    public FrameDefinition transform(List<Event> events, BoardContext ctx) {
        List<EventView> eventViews = events.stream()
                .map(this::toEventView)
                .collect(Collectors.toList());

        DailyScheduleFrameConfig config = DailyScheduleFrameConfig.builder()
                .heading("Today")
                .events(eventViews)
                .build();

        return FrameDefinition.builder()
                .frameType(FrameType.DAILY_SCHEDULE)
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
