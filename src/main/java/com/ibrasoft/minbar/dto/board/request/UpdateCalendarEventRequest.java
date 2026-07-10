package com.ibrasoft.minbar.dto.board.request;

import com.ibrasoft.minbar.model.board.Audience;
import lombok.*;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateCalendarEventRequest {
    private String name;
    private String description;
    private String location;
    private Instant startTime;
    private Instant endTime;
    private Boolean allDay;
    private Audience audience;
}
