package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
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
public class EventListFrameTransformer implements FrameTransformer<List<BoardEvent>> {

    @Override
    public FrameType supports() {
        return FrameType.EVENT_LIST;
    }

    @Override
    public FrameDefinition transform(List<BoardEvent> boardEvents, BoardContext ctx) {
        List<EventView> eventViews = boardEvents.stream()
                .map(EventView::of)
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

}
