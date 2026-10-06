package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateCalendarEventRequest {
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;
    private String description;
    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;
    private Instant startTime;
    private Instant endTime;
    private Boolean allDay;
    private Audience audience;
}
