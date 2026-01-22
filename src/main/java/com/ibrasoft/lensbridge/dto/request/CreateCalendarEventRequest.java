package com.ibrasoft.lensbridge.dto.request;

import com.ibrasoft.lensbridge.model.board.Audience;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCalendarEventRequest {
    
    @NotBlank(message = "Event name is required")
    private String name;
    
    private String description;
    
    private String location;
    
    @Positive(message = "Start timestamp must be positive")
    private long startTimestamp;
    
    @Positive(message = "End timestamp must be positive")
    private long endTimestamp;
    
    private Boolean allDay;
    
    @NotNull(message = "Audience is required")
    private Audience audience;
}
