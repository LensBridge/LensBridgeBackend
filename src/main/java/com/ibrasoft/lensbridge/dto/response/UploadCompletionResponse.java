package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for completed direct upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadCompletionResponse {
    private UUID uploadId;
    private String objectKey;
    private UUID eventId;
    private boolean verified;
    private long fileSize;
}
