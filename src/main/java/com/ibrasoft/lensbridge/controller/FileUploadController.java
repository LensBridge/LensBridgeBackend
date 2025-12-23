package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.dto.response.*;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.R2StorageService;
import com.ibrasoft.lensbridge.service.ThumbnailService;
import com.ibrasoft.lensbridge.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final UploadService uploadService;
    private final R2StorageService r2StorageService;
    private final UploadProperties uploadProperties;
    private final S3Client s3Client;
    private final EventsService eventsService;
    private final ThumbnailService thumbnailService;
    
    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @GetMapping("/{uploadId}")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUploadById(@PathVariable UUID uploadId) {
        try {
            return uploadService.getUploadById(uploadId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of("Error fetching upload: " + e.getMessage()));
        }
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUploadsByEvent(@PathVariable UUID eventId, Pageable pageable) {
        try {
            Page<Upload> uploads = uploadService.getUploadsByEvent(eventId, pageable);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of("Error fetching uploads: " + e.getMessage()));
        }
    }

    /**
     * Generate a presigned URL for direct upload to R2 for a specific event.
     * This allows files to be uploaded directly to R2 without going through the backend.
     */
    @PostMapping("/{eventId}/direct/presign")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> generatePresignedUploadUrl(
            @PathVariable UUID eventId,
            @RequestParam String filename,
            @RequestParam String contentType,
            @RequestParam long fileSize,
            @RequestParam String expectedSha256,
            Authentication authentication) {

        if (!eventsService.isEventAcceptingUploads(eventId) || eventsService.getEventById(eventId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("Event is not accepting uploads"));
        }

        try {
            UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();
            String highestRole = getHighestRole(authentication);
            
            // Check daily limit FIRST
            ResponseEntity<?> limitCheck = checkDailyLimit(user.getId(), highestRole);
            if (limitCheck != null) {
                return limitCheck;
            }
            
            // Validate content type
            if (!uploadProperties.getAllowedFileTypes().contains(contentType)) {
                return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("Content type not allowed: " + contentType));
            }

            // Get user's highest role and check file size limit
            DataSize maxAllowed = uploadProperties.getMaxSizeForRole(highestRole);
            
            if (fileSize > maxAllowed.toBytes()) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(FileSizeErrorResponse.of(
                        "File size exceeds limit for role " + highestRole,
                        maxAllowed.toMegabytes() + "MB",
                        (fileSize / 1024 / 1024) + "MB"
                    ));
            }

            // Generate unique object key matching existing upload structure
            // Images go to "images/" folder, videos go to "videos/" folder with UUID filenames
            String folder;
            if (contentType.startsWith("image")) {
                folder = "images/";
            } else if (contentType.startsWith("video")) {
                folder = "videos/";
            } else {
                return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("Unsupported content type: " + contentType));
            }
            
            // Use UUID as filename to match existing structure
            String objectKey = folder + UUID.randomUUID().toString();

            // Generate presigned URL for upload
            String presignedUrl = r2StorageService.generatePresignedUploadUrl(objectKey, contentType, expectedSha256, fileSize);

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

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL for event: {}", eventId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Failed to generate upload URL: " + e.getMessage()));
        }
    }

    /**
     * Complete a direct upload by creating the Upload entity in the database.
     * This should be called after the file has been successfully uploaded to R2.
     */
    @PostMapping("/{eventId}/direct/complete")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> completeDirectUpload(
            @PathVariable UUID eventId,
            @RequestParam String objectKey,
            @RequestParam String filename,
            @RequestParam String contentType,
            @RequestParam long fileSize,
            @RequestParam(value = "instagramHandle", required = false) String instagramHandle,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "anon", defaultValue = "false") boolean anon,
            @RequestParam String expectedSha256,
            Authentication authentication) {

        try {
            // Verify the object exists in R2
            if (!r2StorageService.objectExists(objectKey)) {
                return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("File not found in storage: " + objectKey));
            }

            // Get object metadata and verify
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
            
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            
            // Verify file size matches
            if (!headResponse.contentLength().equals(fileSize)) {
                return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("File size mismatch in storage"));
            }

            // Verify SHA-256 hash if provided
            if (expectedSha256 != null) {
                try {
                    String actualSha256 = calculateSha256Hash(objectKey);
                    if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                        return ResponseEntity.badRequest()
                            .body(ErrorResponse.of("File integrity verification failed"));
                    }
                } catch (Exception e) {
                    log.error("Failed to verify file hash for {}: {}", objectKey, e.getMessage());
                    return ResponseEntity.badRequest()
                        .body(ErrorResponse.of("Failed to verify file integrity: " + e.getMessage()));
                }
            }

            // Create Upload entity using UploadService
            UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();
            Upload upload = uploadService.createDirectUpload(
                objectKey, filename, contentType, eventId, description, 
                instagramHandle, anon, user.getId()
            );

            // Generate thumbnail asynchronously for images
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
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to complete direct upload for event '{}', objectKey: {}", eventId, objectKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Failed to complete upload: " + e.getMessage()));
        }
    }

    /**
     * Get upload limits for the current user.
     */
    @GetMapping("/limits")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUploadLimits(Authentication authentication) {
        try {
            UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();
            String highestRole = getHighestRole(authentication);
            DataSize maxSize = uploadProperties.getMaxSizeForRole(highestRole);
            int dailyLimit = uploadProperties.getDailyLimitForRole(highestRole);
            long uploadsToday = uploadService.countUploadsToday(user.getId());

            UploadLimitsResponse limits = UploadLimitsResponse.builder()
                .role(highestRole)
                .maxSizeBytes(maxSize.toBytes())
                .maxSizeMB(maxSize.toMegabytes())
                .allowedContentTypes(uploadProperties.getAllowedFileTypes())
                .videoMaxDurationSeconds(uploadProperties.getVideoMaxduration())
                .dailyLimit(dailyLimit)
                .uploadsToday(uploadsToday)
                .uploadsRemaining(Math.max(0, dailyLimit - uploadsToday))
                .build();

            return ResponseEntity.ok(limits);
        } catch (Exception e) {
            log.error("Failed to get upload limits", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Failed to get upload limits"));
        }
    }

    /**
     * Check if the user has reached their daily upload limit.
     * @param userId The user ID to check
     * @param role The user's highest role
     * @return ResponseEntity with error if limit exceeded, null otherwise
     */
    private ResponseEntity<?> checkDailyLimit(UUID userId, String role) {
        int dailyLimit = uploadProperties.getDailyLimitForRole(role);
        
        if (uploadService.hasReachedDailyLimit(userId, dailyLimit)) {
            long uploadCount = uploadService.countUploadsToday(userId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(DailyLimitErrorResponse.of(
                    "Daily upload limit exceeded",
                    dailyLimit,
                    uploadCount,
                    role
                ));
        }
        return null; // No limit exceeded
    }

    /**
     * Get the highest role for the authenticated user.
     */
    private String getHighestRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return "verified"; // Default to verified (lowest role)
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        if (authorities.stream().anyMatch(a -> Role.ROOT.equals(a.getAuthority()))) {
            return "root";
        }
        if (authorities.stream().anyMatch(a -> Role.ADMIN.equals(a.getAuthority()))) {
            return "admin";
        }
        if (authorities.stream().anyMatch(a -> Role.MODERATOR.equals(a.getAuthority()))) {
            return "moderator";
        }
        if (authorities.stream().anyMatch(a -> Role.VERIFIED.equals(a.getAuthority()))) {
            return "verified";
        }
        
        return "verified"; // Default to verified
    }

    /**
     * Calculate SHA-256 hash of the uploaded file.
     */
    private String calculateSha256Hash(String objectKey) throws Exception {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

            ResponseInputStream<GetObjectResponse> objectStream = s3Client.getObject(getObjectRequest);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = objectStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            
            objectStream.close();
            
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        } catch (Exception e) {
            throw new Exception("Failed to calculate file hash: " + e.getMessage(), e);
        }
    }
}
