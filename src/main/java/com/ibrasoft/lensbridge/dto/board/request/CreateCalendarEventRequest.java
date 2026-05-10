package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateCalendarEventRequest {
    @NotBlank(message = "Event name is required")
    private String name;
    @NotBlank(message = "Event description is required")
    private String description;
    @NotBlank(message = "Event location is required")
    @Size(max = 255, message = "Location must be less than 255 characters")
    private String location;
    @NotNull(message = "Start time is required")
    private Instant startTime;
    @NotNull(message = "End time is required")
    private Instant endTime;
    private Boolean allDay;
    @NotNull(message = "Audience is required")
    private Audience audience;
}
