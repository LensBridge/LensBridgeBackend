package com.ibrasoft.lensbridge.dto.request;

import java.time.LocalDate;

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
public class CreatePosterRequest {
    
    @NotBlank(message = "Title is required")
    private String title;
    
    /**
     * Duration in seconds for how long the poster should be displayed.
     */
    @Positive(message = "Duration must be positive")
    private int duration;
    
    /**
     * The date from which the poster becomes active (inclusive).
     */
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
    
    /**
     * The date until which the poster remains active (exclusive).
     */
    @NotNull(message = "End date is required")
    private LocalDate endDate;
    
    /**
     * Target audience for the poster.
     */
    @NotNull(message = "Audience is required")
    private Audience audience;
}
