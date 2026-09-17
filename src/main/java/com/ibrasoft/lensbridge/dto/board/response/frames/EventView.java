package com.ibrasoft.lensbridge.dto.board.response.frames;

import com.ibrasoft.lensbridge.model.board.BoardEvent;
import lombok.*;

import java.time.Instant;

@Data 
@Builder 
@NoArgsConstructor 
@AllArgsConstructor
/**
 * Read-only view of a {@link BoardEvent} for REST responses.
 */
public class EventView {
    private String name;
    private String description;
    private String location;
    private Instant startTime;
    private Instant endTime;
    private Boolean allDay;

    public static EventView of(BoardEvent event) {
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
