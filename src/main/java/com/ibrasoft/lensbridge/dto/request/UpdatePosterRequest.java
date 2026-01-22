package com.ibrasoft.lensbridge.dto.request;

import java.time.LocalDate;

import com.ibrasoft.lensbridge.model.board.Audience;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating poster information.
 * All fields are optional - only non-null fields will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePosterRequest {
    
    private String title;
    
    /**
     * Duration in seconds for how long the poster should be displayed.
     */
    @Positive(message = "Duration must be positive")
    private Integer duration;
    
    /**
     * The date from which the poster becomes active (inclusive).
     */
    private LocalDate startDate;
    
    /**
     * The date until which the poster remains active (exclusive).
     */
    private LocalDate endDate;
    
    /**
     * Target audience for the poster.
     */
    private Audience audience;
}
