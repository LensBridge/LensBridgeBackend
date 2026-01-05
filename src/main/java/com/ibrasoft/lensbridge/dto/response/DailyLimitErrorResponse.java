package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error response DTO for daily upload limit exceeded.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyLimitErrorResponse {
    private String error;
    private int dailyLimit;
    private long uploadsToday;
    private String role;
    
    public static DailyLimitErrorResponse of(String error, int dailyLimit, long uploadsToday, String role) {
        return new DailyLimitErrorResponse(error, dailyLimit, uploadsToday, role);
    }
}
