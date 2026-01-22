package com.ibrasoft.lensbridge.dto.request;

import com.ibrasoft.lensbridge.model.board.Audience;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating calendar event information.
 * All fields are optional - only non-null fields will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCalendarEventRequest {
    
    private String name;
    
    private String description;
    
    private String location;
    
    @Positive(message = "Start timestamp must be positive")
    private Long startTimestamp;
    
    @Positive(message = "End timestamp must be positive")
    private Long endTimestamp;
    
    private Boolean allDay;
    
    private Audience audience;
}
