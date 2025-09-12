package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for upload limits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadLimitsResponse {
    private String role;
    private long maxSizeBytes;
    private long maxSizeMB;
    private List<String> allowedContentTypes;
    private int videoMaxDurationSeconds;
    private int dailyLimit;
    private long uploadsToday;
    private long uploadsRemaining;
}
