package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.R2StorageService;
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
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final UploadService uploadService;
    private final R2StorageService r2StorageService;
    private final UploadProperties uploadProperties;
    private final S3Client s3Client;
    
    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @PostMapping("/{eventId}/batch")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> uploadFiles(@PathVariable UUID eventId, @RequestParam("files") List<MultipartFile> files, @RequestParam(value = "instagramHandle", required = false) String instagramHandle, @RequestParam(value = "description", required = false) String description, @RequestParam(value = "anon") boolean anon) {
        List<UUID> uploadedUuids = new ArrayList<>();
        UserDetailsImpl user = (UserDetailsImpl) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty: " + file.getOriginalFilename());
            }

            Upload upload = uploadService.createUpload(file, eventId, description, instagramHandle, anon, user.getId());
            uploadedUuids.add(upload.getUuid());
        }
        return ResponseEntity.ok("Files uploaded successfully. UUIDs: " + uploadedUuids);
    }

    @GetMapping("/{uploadId}")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUploadById(@PathVariable UUID uploadId) {
        try {
            return uploadService.getUploadById(uploadId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching upload: " + e.getMessage());
        }
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUploadsByEvent(@PathVariable UUID eventId, Pageable pageable) {
        try {
            Page<Upload> uploads = uploadService.getUploadsByEvent(eventId, pageable);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching uploads: " + e.getMessage());
        }
    }

    // ===== DIRECT-TO-R2 UPLOAD METHODS =====

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
            @RequestParam(required = false) String expectedSha256,
            Authentication authentication) {

        try {
            // Validate content type
            if (!uploadProperties.getAllowedFileTypes().contains(contentType)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Content type not allowed: " + contentType));
            }

            // Get user's highest role and check file size limit
            String highestRole = getHighestRole(authentication);
            DataSize maxAllowed = uploadProperties.getMaxSizeForRole(highestRole);
            
            if (fileSize > maxAllowed.toBytes()) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of(
                        "error", "File size exceeds limit for role " + highestRole,
                        "maxAllowed", maxAllowed.toMegabytes() + "MB",
                        "requested", (fileSize / 1024 / 1024) + "MB"
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
                    .body(Map.of("error", "Unsupported content type: " + contentType));
            }
            
            // Use UUID as filename to match existing structure
            String objectKey = folder + UUID.randomUUID().toString();

            // Generate presigned URL for upload
            String presignedUrl = r2StorageService.generatePresignedUploadUrl(objectKey, contentType, expectedSha256, fileSize);

            Map<String, Object> response = new HashMap<>();
            response.put("uploadUrl", presignedUrl);
            response.put("objectKey", objectKey);
            response.put("eventId", eventId);
            response.put("method", "PUT");
            response.put("contentType", contentType);
            response.put("expiresInMinutes", 15);
            
            if (expectedSha256 != null) {
                response.put("expectedSha256", expectedSha256);
            }

            log.info("Generated presigned upload URL for event '{}', user role '{}', file: '{}', size: {}MB", 
                eventId, highestRole, filename, fileSize / 1024 / 1024);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL for event: {}", eventId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate upload URL: " + e.getMessage()));
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
            @RequestParam(required = false) String expectedSha256,
            Authentication authentication) {

        try {
            // Verify the object exists in R2
            if (!r2StorageService.objectExists(objectKey)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "File not found in storage: " + objectKey));
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
                    .body(Map.of("error", "File size mismatch in storage"));
            }

            // Verify SHA-256 hash if provided
            if (expectedSha256 != null) {
                try {
                    String actualSha256 = calculateSha256Hash(objectKey);
                    if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                        return ResponseEntity.badRequest()
                            .body(Map.of("error", "File integrity verification failed"));
                    }
                } catch (Exception e) {
                    log.error("Failed to verify file hash for {}: {}", objectKey, e.getMessage());
                    return ResponseEntity.badRequest()
                        .body(Map.of("error", "Failed to verify file integrity: " + e.getMessage()));
                }
            }

            // Create Upload entity using UploadService
            UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();
            Upload upload = uploadService.createDirectUpload(
                objectKey, filename, contentType, eventId, description, 
                instagramHandle, anon, user.getId()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("uploadId", upload.getUuid());
            response.put("objectKey", objectKey);
            response.put("eventId", eventId);
            response.put("verified", true);
            response.put("fileSize", fileSize);
            
            // Generate secure URL for the uploaded file
            String secureUrl = r2StorageService.getSecureUrl(objectKey, upload.isApproved(), isAdmin(authentication));
            response.put("secureUrl", secureUrl);

            log.info("Successfully completed direct upload for event '{}': {}", eventId, upload.getUuid());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to complete direct upload for event '{}', objectKey: {}", eventId, objectKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to complete upload: " + e.getMessage()));
        }
    }

    /**
     * Get upload limits for the current user.
     */
    @GetMapping("/limits")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUploadLimits(Authentication authentication) {
        try {
            String highestRole = getHighestRole(authentication);
            DataSize maxSize = uploadProperties.getMaxSizeForRole(highestRole);

            Map<String, Object> limits = new HashMap<>();
            limits.put("role", highestRole);
            limits.put("maxSizeBytes", maxSize.toBytes());
            limits.put("maxSizeMB", maxSize.toMegabytes());
            limits.put("allowedContentTypes", uploadProperties.getAllowedFileTypes());
            limits.put("videoMaxDurationSeconds", uploadProperties.getVideoMaxduration());

            return ResponseEntity.ok(limits);
        } catch (Exception e) {
            log.error("Failed to get upload limits", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get upload limits"));
        }
    }

    // ===== HELPER METHODS =====

    /**
     * Get the highest role for the authenticated user.
     */
    private String getHighestRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return "user";
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
        if (authorities.stream().anyMatch(a -> Role.USER.equals(a.getAuthority()))) {
            return "user";
        }
        
        return "user";
    }

    /**
     * Check if the user has admin privileges.
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(a -> Role.ADMIN.equals(a.getAuthority()) || Role.ROOT.equals(a.getAuthority()));
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
