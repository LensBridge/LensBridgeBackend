package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateCalendarEventRequest {
    @NotBlank(message = "Event name is required")
    @Size(min = 1, max = 255, message = "Name must be at most 255 characters")
    private String name;
    
    @NotBlank(message = "Event description is required")
    private String description;
    
    @NotBlank(message = "Event location is required")
    @Size(min = 1, max = 255, message = "Location must be at most 255 characters")
    private String location;
    
    // Boxed: @NotNull on a primitive never fires (an absent field deserialises to 0), so an
    // omitted time used to create an event in 1970.
    @NotNull(message = "Start time is required")
    private Long startEpochMs;
    
    @NotNull(message = "End time is required")
    private Long endEpochMs;
    
    /** Optional: null is treated as false. */
    private Boolean allDay;
    
    @NotNull(message = "Audience is required")
    private Audience audience;
}
