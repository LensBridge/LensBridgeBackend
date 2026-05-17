package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.dto.upload.response.PresignedUploadResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadCompletionResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.exception.EventNotAcceptingUploadsException;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.upload.Upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadWorkflowService {

    private final UploadService uploadService;
    private final R2StorageService r2StorageService;
    private final UploadLimitsService uploadLimitsService;
    private final EventsService eventsService;
    private final ImageProcessingService imageProcessingService;

    public PresignedUploadResponse initiateUpload(
            UUID eventId,
            String filename,
            String contentType,
            long fileSize,
            String expectedSha256,
            UUID userId,
            Role role) {

        if (!eventsService.isEventAcceptingUploads(eventId)) {
            throw new EventNotAcceptingUploadsException(eventId);
        }

        uploadLimitsService.validateUpload(userId, role, fileSize, contentType);

        String objectKey = "images/" + UUID.randomUUID();
        String presignedUrl = r2StorageService.generatePresignedUploadUrl(objectKey, contentType, fileSize);

        PresignedUploadResponse.PresignedUploadResponseBuilder builder = PresignedUploadResponse.builder()
                .uploadUrl(presignedUrl)
                .objectKey(objectKey)
                .eventId(eventId)
                .method("PUT")
                .contentType(contentType)
                .expiresInMinutes(15)
                .expectedSha256(expectedSha256);

        log.info("Presigned upload initiated for event {}, role {}, file: {}, size: {}MB",
                eventId, role, filename, fileSize / 1024 / 1024);

        return builder.build();
    }

    public UploadCompletionResponse completeUpload(
            UUID eventId,
            String objectKey,
            String filename,
            String contentType,
            long fileSize,
            String instagramHandle,
            String description,
            boolean anon,
            String expectedSha256,
            UUID userId) {

        if (!r2StorageService.objectExists(objectKey)) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("File not found in storage: " + objectKey),
                    "File not found in storage");
        }

        try {
            verifyIntegrity(objectKey, fileSize, expectedSha256);
        } catch (ApiResponseException e) {
            r2StorageService.deleteObject(objectKey);
            throw e;
        }

        Upload upload = uploadService.createUpload(
                objectKey, filename, eventId, description, instagramHandle, anon, userId);

        imageProcessingService.generateThumbnail(upload);

        log.info("Upload completed for event {}: {}", eventId, upload.getUuid());

        return UploadCompletionResponse.builder()
                .uploadId(upload.getUuid())
                .objectKey(objectKey)
                .eventId(eventId)
                .verified(true)
                .fileSize(fileSize)
                .build();
    }

    private void verifyIntegrity(String objectKey, long expectedSize, String expectedSha256) {
        try {
            long storedSize = r2StorageService.getObjectMetadata(objectKey).contentLength();
            if (storedSize != expectedSize) {
                throw new ApiResponseException(
                        HttpStatus.BAD_REQUEST,
                        ErrorResponse.of("File size mismatch: expected " + expectedSize + " bytes, got " + storedSize),
                        "File size mismatch");
            }

            String actualHash = r2StorageService.calculateSha256Hash(objectKey);
            if (!actualHash.equalsIgnoreCase(expectedSha256)) {
                throw new ApiResponseException(
                        HttpStatus.BAD_REQUEST,
                        ErrorResponse.of("File integrity check failed"),
                        "SHA-256 mismatch");
            }

        } catch (ApiResponseException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Integrity verification failed for {}: {}", objectKey, e.getMessage());
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("Failed to verify file integrity"),
                    "Integrity verification error");
        }
    }
}
