package com.ibrasoft.lensbridge.dto.request;

import com.ibrasoft.lensbridge.model.board.IslamicQuote;
import com.ibrasoft.lensbridge.model.board.JummahPrayer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating weekly content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyContentRequest {
    
    @NotNull(message = "Year is required")
    @Min(value = 2020, message = "Year must be 2020 or later")
    @Max(value = 2100, message = "Year must be before 2100")
    private Integer year;
    
    @NotNull(message = "Week number is required")
    @Min(value = 1, message = "Week number must be between 1 and 53")
    @Max(value = 53, message = "Week number must be between 1 and 53")
    private Integer weekNumber;
    
    /**
     * Quranic verse for the week.
     */
    private IslamicQuote verse;
    
    /**
     * Hadith for the week.
     */
    private IslamicQuote hadith;
    
    /**
     * Jummah prayer details for the week.
     */
    private JummahPrayer[] jummahPrayer;
}
