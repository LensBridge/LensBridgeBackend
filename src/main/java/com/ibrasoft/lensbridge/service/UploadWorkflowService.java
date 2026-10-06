package com.ibrasoft.lensbridge.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.dto.upload.response.PresignedUploadResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadCompletionResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.exception.EventNotAcceptingUploadsException;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.upload.Upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Two-step direct-to-R2 upload: {@link #initiateUpload} hands out a presigned PUT URL for a
 * fresh object key, the client uploads, then {@link #completeUpload} verifies the object and
 * records it.
 * <p>
 * The object key in the second step comes from the client, so it is never trusted on its own:
 * it must be one this user was issued, and only such a key may ever be verified, recorded or
 * deleted. Issued keys are remembered in memory, like the login attempts and rate limits, which
 * assumes a single instance; a restart (or a second instance) between the two steps makes
 * {@code complete} fail and the client has to start the upload again.
 */
@Service
@Slf4j
public class UploadWorkflowService {

    /** Exactly what {@link #initiateUpload} issues, so a client cannot name any other object. */
    private static final Pattern ISSUED_KEY = Pattern.compile(
            "images/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Kept past the URL's expiry because a PUT can start just before it and finish after. */
    private static final Duration ISSUED_KEY_GRACE = Duration.ofMinutes(5);

    private static final long MAX_ISSUED_KEYS = 10_000;

    private final UploadService uploadService;
    private final R2StorageService r2StorageService;
    private final UploadLimitsService uploadLimitsService;
    private final EventsService eventsService;
    private final ImageProcessingService imageProcessingService;

    /** Object key to the user it was issued to. */
    private final Cache<String, UUID> issuedKeys;
    /** Keys being completed right now, so two concurrent requests cannot both record one object. */
    private final Set<String> completing = ConcurrentHashMap.newKeySet();

    public UploadWorkflowService(UploadService uploadService,
                                 R2StorageService r2StorageService,
                                 UploadLimitsService uploadLimitsService,
                                 EventsService eventsService,
                                 ImageProcessingService imageProcessingService) {
        this.uploadService = uploadService;
        this.r2StorageService = r2StorageService;
        this.uploadLimitsService = uploadLimitsService;
        this.eventsService = eventsService;
        this.imageProcessingService = imageProcessingService;
        this.issuedKeys = Caffeine.newBuilder()
                .maximumSize(MAX_ISSUED_KEYS)
                .expireAfterWrite(Duration.ofMinutes(r2StorageService.getUrlExpirationMinutes())
                        .plus(ISSUED_KEY_GRACE))
                .build();
    }

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
        issuedKeys.put(objectKey, userId);

        PresignedUploadResponse.PresignedUploadResponseBuilder builder = PresignedUploadResponse.builder()
                .uploadUrl(presignedUrl)
                .objectKey(objectKey)
                .eventId(eventId)
                .method("PUT")
                .contentType(contentType)
                .expiresInMinutes((int) r2StorageService.getUrlExpirationMinutes())
                .expectedSha256(expectedSha256);

        log.info("Presigned upload initiated for event {}, role {}, file: {}, size: {}MB",
                eventId, role, filename, fileSize / 1024 / 1024);

        return builder.build();
    }

    /**
     * @param contentType the client's claim, unused: the limits are checked against the content
     *                    type and size R2 reports for the stored object
     */
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
            UUID userId,
            Role role) {

        requireIssuedTo(objectKey, userId);

        if (!completing.add(objectKey)) {
            throw badRequest("This upload is already being completed");
        }
        try {
            if (uploadService.isObjectKeyInUse(objectKey)) {
                throw badRequest("This file has already been submitted");
            }

            if (!r2StorageService.objectExists(objectKey)) {
                throw badRequest("File not found in storage: " + objectKey);
            }

            verifyStoredObject(eventId, objectKey, fileSize, expectedSha256, userId, role);

            Upload upload = uploadService.createUpload(
                    objectKey, filename, eventId, description, instagramHandle, anon, userId);
            issuedKeys.invalidate(objectKey);

            imageProcessingService.generateThumbnail(upload);

            log.info("Upload completed for event {}: {}", eventId, upload.getUuid());

            return UploadCompletionResponse.builder()
                    .uploadId(upload.getUuid())
                    .objectKey(objectKey)
                    .eventId(eventId)
                    .verified(true)
                    .fileSize(fileSize)
                    .build();
        } finally {
            completing.remove(objectKey);
        }
    }

    /**
     * Rejects, before R2 is touched, any key that is not one this user was just issued. This is
     * what stops a client naming someone else's object (or a poster, or a thumbnail) and having
     * it claimed as their own upload or deleted by a failed integrity check.
     */
    private void requireIssuedTo(String objectKey, UUID userId) {
        if (objectKey == null || !ISSUED_KEY.matcher(objectKey).matches()
                || !userId.equals(issuedKeys.getIfPresent(objectKey))) {
            throw badRequest("Unknown or expired upload; start the upload again");
        }
    }

    /**
     * Checks what R2 actually holds against the rules, not against the client's description of it.
     * A rejection (closed event, a limit, a size or hash mismatch) deletes the object: the key is
     * one this user was issued, so the file would otherwise sit in the bucket unreferenced. A
     * failure to read the object at all is not a verdict on it, so it is left for a retry.
     */
    private void verifyStoredObject(UUID eventId, String objectKey, long expectedSize,
            String expectedSha256, UUID userId, Role role) {
        R2StorageService.R2ObjectMetadata stored;
        try {
            stored = r2StorageService.getObjectMetadata(objectKey);
        } catch (Exception e) {
            throw unverifiable(objectKey, e);
        }

        if (!eventsService.isEventAcceptingUploads(eventId)) {
            throw rejected(objectKey, new EventNotAcceptingUploadsException(eventId));
        }

        if (stored.contentLength() != expectedSize) {
            throw rejected(objectKey, new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("File size mismatch: expected " + expectedSize
                            + " bytes, got " + stored.contentLength()),
                    "File size mismatch"));
        }

        try {
            uploadLimitsService.validateUpload(userId, role, stored.contentLength(), stored.contentType());
        } catch (RuntimeException e) {
            throw rejected(objectKey, e);
        }

        String actualHash;
        try {
            actualHash = r2StorageService.calculateSha256Hash(objectKey);
        } catch (Exception e) {
            throw unverifiable(objectKey, e);
        }
        if (!actualHash.equalsIgnoreCase(expectedSha256)) {
            throw rejected(objectKey, new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("File integrity check failed"),
                    "SHA-256 mismatch"));
        }
    }

    private RuntimeException rejected(String objectKey, RuntimeException cause) {
        issuedKeys.invalidate(objectKey);
        try {
            r2StorageService.deleteObject(objectKey);
        } catch (Exception e) {
            log.warn("Could not delete rejected object {}: {}", objectKey, e.getMessage());
        }
        return cause;
    }

    private ApiResponseException unverifiable(String objectKey, Exception cause) {
        log.warn("Integrity verification failed for {}: {}", objectKey, cause.getMessage());
        return badRequest("Failed to verify file integrity");
    }

    private static ApiResponseException badRequest(String message) {
        return new ApiResponseException(HttpStatus.BAD_REQUEST, ErrorResponse.of(message), message);
    }
}
