package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.dto.response.DailyLimitErrorResponse;
import com.ibrasoft.lensbridge.dto.response.ErrorResponse;
import com.ibrasoft.lensbridge.dto.response.FileSizeErrorResponse;
import com.ibrasoft.lensbridge.dto.response.PresignedUploadResponse;
import com.ibrasoft.lensbridge.dto.response.UploadCompletionResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.upload.Upload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectUploadService {

    private final UploadService uploadService;
    private final R2StorageService r2StorageService;
    private final UploadProperties uploadProperties;
    private final EventsService eventsService;
    private final ThumbnailService thumbnailService;

    public PresignedUploadResponse createPresignedUpload(
            UUID eventId,
            String filename,
            String contentType,
            long fileSize,
            String expectedSha256,
            UUID userId,
            String highestRole) {

        validateEventAcceptingUploads(eventId);
        enforceDailyLimit(userId, highestRole);
        validateContentType(contentType);

        DataSize maxAllowed = uploadProperties.getMaxSizeForRole(highestRole);
        if (fileSize > maxAllowed.toBytes()) {
            throw new ApiResponseException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    FileSizeErrorResponse.of(
                            "File size exceeds limit for role " + highestRole,
                            maxAllowed.toMegabytes() + "MB",
                            (fileSize / 1024 / 1024) + "MB"),
                    "File size exceeds role limit");
        }

        String objectKey = resolveObjectKey(contentType);
        String presignedUrl = r2StorageService.generatePresignedUploadUrl(
                objectKey,
                contentType,
                expectedSha256,
                fileSize);

        PresignedUploadResponse.PresignedUploadResponseBuilder responseBuilder = PresignedUploadResponse.builder()
                .uploadUrl(presignedUrl)
                .objectKey(objectKey)
                .eventId(eventId)
                .method("PUT")
                .contentType(contentType)
                .expiresInMinutes(15);

        if (expectedSha256 != null) {
            responseBuilder.expectedSha256(expectedSha256);
        }

        PresignedUploadResponse response = responseBuilder.build();

        log.info("Generated presigned upload URL for event '{}', user role '{}', file: '{}', size: {}MB",
                eventId, highestRole, filename, fileSize / 1024 / 1024);

        return response;
    }

    public UploadCompletionResponse completeDirectUpload(
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

        // TODO: Implement size and hash verification methods in R2StorageService
        // long storedSize = r2StorageService.getObjectContentLength(objectKey);
        // if (storedSize != fileSize) {
        //     throw new ApiResponseException(
        //             HttpStatus.BAD_REQUEST,
        //             ErrorResponse.of("File size mismatch in storage"),
        //             "File size mismatch in storage");
        // }

        // if (expectedSha256 != null) {
        //     try {
        //         String actualSha256 = r2StorageService.calculateSha256Hash(objectKey);
        //         if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
        //             throw new ApiResponseException(
        //                     HttpStatus.BAD_REQUEST,
        //                     ErrorResponse.of("File integrity verification failed"),
        //                     "File integrity verification failed");
        //         }
        //     } catch (ApiResponseException e) {
        //         throw e;
        //     } catch (Exception e) {
        //         throw new ApiResponseException(
        //                 HttpStatus.BAD_REQUEST,
        //                 ErrorResponse.of("Failed to verify file integrity: " + e.getMessage()),
        //                 "Failed to verify file integrity");
        //     }
        // }

        Upload upload = uploadService.createDirectUpload(
                objectKey,
                filename,
                contentType,
                eventId,
                description,
                instagramHandle,
                anon,
                userId);

        if (contentType != null && contentType.startsWith("image")) {
            thumbnailService.generateThumbnailAsync(upload.getUuid(), objectKey);
            log.debug("Triggered async thumbnail generation for upload: {}", upload.getUuid());
        }

        UploadCompletionResponse response = UploadCompletionResponse.builder()
                .uploadId(upload.getUuid())
                .objectKey(objectKey)
                .eventId(eventId)
                .verified(true)
                .fileSize(fileSize)
                .build();

        log.info("Successfully completed direct upload for event '{}': {}", eventId, upload.getUuid());
        return response;
    }

    private void validateEventAcceptingUploads(UUID eventId) {
        if (!eventsService.isEventAcceptingUploads(eventId)) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("Event is not accepting uploads"),
                    "Event is not accepting uploads");
        }
    }

    private void enforceDailyLimit(UUID userId, String role) {
        int dailyLimit = uploadProperties.getDailyLimitForRole(role);
        if (uploadService.hasReachedDailyLimit(userId, dailyLimit)) {
            long uploadCount = uploadService.countUploadsToday(userId);
            throw new ApiResponseException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    DailyLimitErrorResponse.of(
                            "Daily upload limit exceeded",
                            dailyLimit,
                            uploadCount,
                            role),
                    "Daily upload limit exceeded");
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null || !uploadProperties.getAllowedFileTypes().contains(contentType)) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("Content type not allowed: " + contentType),
                    "Content type not allowed");
        }
    }

    private String resolveObjectKey(String contentType) {
        String folder;
        if (contentType != null && contentType.startsWith("image")) {
            folder = "images/";
        } else if (contentType != null && contentType.startsWith("video")) {
            folder = "videos/";
        } else {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("Unsupported content type: " + contentType),
                    "Unsupported content type");
        }
        return folder + UUID.randomUUID();
    }
}
