package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error response DTO for file size limit exceeded.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileSizeErrorResponse {
    private String error;
    private String maxAllowed;
    private String requested;
    
    public static FileSizeErrorResponse of(String error, String maxAllowed, String requested) {
        return new FileSizeErrorResponse(error, maxAllowed, requested);
    }
}
