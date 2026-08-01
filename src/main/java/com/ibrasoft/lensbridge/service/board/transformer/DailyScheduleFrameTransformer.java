package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
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
public class DailyScheduleFrameTransformer implements FrameTransformer<List<BoardEvent>> {

    @Override
    public FrameType supports() {
        return FrameType.DAILY_SCHEDULE;
    }

    @Override
    public FrameDefinition transform(List<BoardEvent> boardEvents, BoardContext ctx) {
        List<EventView> eventViews = boardEvents.stream()
                .map(EventView::of)
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

}
