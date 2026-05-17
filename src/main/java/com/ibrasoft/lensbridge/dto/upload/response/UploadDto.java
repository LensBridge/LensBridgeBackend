package com.ibrasoft.lensbridge.dto.upload.response;

import com.ibrasoft.lensbridge.model.upload.UploadType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadDto {
    private UUID uuid;
    private String fileName;
    private String fileUrl;
    private String thumbnailUrl;
    private String uploadDescription;
    private String instagramHandle;
    private UUID uploadedBy;
    private UUID eventId;
    private String eventName;
    private Instant createdDate;
    private boolean approved;
    private boolean featured;
    private boolean isAnon;
    private UploadType contentType;
}
