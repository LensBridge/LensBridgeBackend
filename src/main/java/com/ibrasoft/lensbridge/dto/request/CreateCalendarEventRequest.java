package com.ibrasoft.lensbridge.dto.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateCalendarEventRequest {
    @NotBlank(message = "Event name is required")
    private String name;
    private String description;
    private String location;
    @NotNull(message = "Start time is required")
    private Long startEpochMs;
    @NotNull(message = "End time is required")
    private Long endEpochMs;
    private Boolean allDay;
    @NotNull(message = "Audience is required")
    private Audience audience;
}
