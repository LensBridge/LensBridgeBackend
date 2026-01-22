package com.ibrasoft.lensbridge.dto.request;

import com.ibrasoft.lensbridge.model.board.Location;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for updating board configuration.
 * All fields are optional - only non-null fields will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoardConfigRequest {
    
    /**
     * The prayer location details.
     */
    private Location location;
    
    /**
     * Default poster cycle duration in milliseconds.
     */
    @Min(value = 1000, message = "Poster cycle interval must be at least 1000ms")
    private Integer posterCycleInterval;
    
    /**
     * How long to wait before refreshing data after Ishaa.
     */
    @Min(value = 0, message = "Refresh after Ishaa minutes cannot be negative")
    private Integer refreshAfterIshaaMinutes;
    
    /**
     * Enable dark mode after Ishaa.
     */
    private Boolean darkModeAfterIsha;
    
    /**
     * How long to wait after isha (in minutes) before enabling dark mode.
     */
    @Min(value = 0, message = "Dark mode minutes after Isha cannot be negative")
    private Integer darkModeMinutesAfterIsha;
    
    /**
     * Enable scrolling message at the bottom of the screen.
     */
    private Boolean enableScrollingMessage;
    
    /**
     * The scrolling message texts (will rotate through them).
     */
    private List<String> scrollingMessages;
}
