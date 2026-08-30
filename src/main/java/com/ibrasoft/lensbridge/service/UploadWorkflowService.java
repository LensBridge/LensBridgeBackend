package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.dto.upload.response.PresignedUploadResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadCompletionResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.exception.EventNotAcceptingUploadsException;
import com.ibrasoft.lensbridge.exception.ImageDimensionsExceededException;
import com.ibrasoft.lensbridge.exception.InvalidContentTypeException;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadWorkflowService {

    private final UploadService uploadService;
    private final R2StorageService r2StorageService;
    private final UploadLimitsService uploadLimitsService;
    private final BoardEventRepository boardEventRepository;
    private final ImageProcessingService imageProcessingService;
    private final ImageValidationService imageValidationService;

    @Value("${lensbridge.app.daysCutoffForPastEvent:7}")
    private int daysCutoffForPastEvent;

    public PresignedUploadResponse initiateUpload(
            UUID eventId,
            String filename,
            String contentType,
            long fileSize,
            String expectedSha256,
            UUID userId,
            User user) {

        validateEventAcceptsUploads(eventId);
        requireVerified(user);

        uploadLimitsService.validateUpload(userId, user, fileSize, contentType);

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

        log.info("Presigned upload initiated for event {}, user {}, file: {}, size: {}MB",
                eventId, user.getEmail(), filename, fileSize / 1024 / 1024);

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
            verifyIntegrity(objectKey, fileSize, expectedSha256, contentType);
        } catch (ApiResponseException | InvalidContentTypeException | ImageDimensionsExceededException e) {
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

    private void validateEventAcceptsUploads(UUID eventId) {
        BoardEvent event = boardEventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotAcceptingUploadsException(eventId));
        if (!Boolean.TRUE.equals(event.getAllowUploads())) {
            throw new EventNotAcceptingUploadsException(eventId);
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(daysCutoffForPastEvent));
        if (event.getEndTime().isBefore(cutoff)) {
            throw new EventNotAcceptingUploadsException(eventId);
        }
    }

    private void requireVerified(User user) {
        if (!user.isVerified()) {
            throw new ApiResponseException(
                    HttpStatus.FORBIDDEN,
                    ErrorResponse.of("Only verified users can upload"),
                    "User not verified");
        }
    }

    /**
     * Establishes that the stored object is what the client said it uploaded.
     * <p>
     * Size and hash only prove the bytes are the ones the client intended to send — they say
     * nothing about whether those bytes are an image at all. {@link ImageValidationService} then
     * sniffs the real container type and reads the image header, so a ZIP declared as
     * {@code image/png} and a decompression bomb are both rejected here, before the object gets
     * an {@code Upload} row and before the async thumbnail worker tries to decode it.
     */
    private void verifyIntegrity(String objectKey, long expectedSize, String expectedSha256,
            String declaredContentType) {
        byte[] bytes;
        try {
            long storedSize = r2StorageService.getObjectMetadata(objectKey).contentLength();
            if (storedSize != expectedSize) {
                throw new ApiResponseException(
                        HttpStatus.BAD_REQUEST,
                        ErrorResponse.of("File size mismatch: expected " + expectedSize + " bytes, got " + storedSize),
                        "File size mismatch");
            }

            // One fetch serves both the hash and the content checks below; the size check above
            // has already bounded how much we are about to pull into the heap.
            bytes = r2StorageService.getObjectBytes(objectKey);

            String actualHash = sha256Hex(bytes);
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

        // Outside the catch above: these throw their own mapped exceptions, and wrapping them in
        // a generic "failed to verify integrity" would hide why the upload was refused.
        imageValidationService.validateImageBytes(bytes, declaredContentType);
    }

    private static String sha256Hex(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
