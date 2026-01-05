package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for presigned upload URL generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResponse {
    private String uploadUrl;
    private String objectKey;
    private UUID eventId;
    private String method;
    private String contentType;
    private int expiresInMinutes;
    private String expectedSha256;
}
