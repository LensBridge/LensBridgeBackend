package com.ibrasoft.lensbridge.dto.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateCalendarEventRequest {
    private String name;
    private String description;
    private String location;
    private Long startEpochMs;
    private Long endEpochMs;
    private Boolean allDay;
    private Audience audience;
}
