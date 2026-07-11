package com.ibrasoft.minbar.signage.dto.request;

import com.ibrasoft.minbar.signage.model.Audience;
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
