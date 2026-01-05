package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.dto.response.*;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.DirectUploadService;
import com.ibrasoft.lensbridge.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.*;
import java.util.Collection;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final UploadService uploadService;
    private final DirectUploadService directUploadService;
    private final UploadProperties uploadProperties;

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
        try {
            UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();
            String highestRole = getHighestRole(authentication);

            PresignedUploadResponse response = directUploadService.createPresignedUpload(
                    eventId,
                    filename,
                    contentType,
                    fileSize,
                    expectedSha256,
                    user.getId(),
                    highestRole);

            return ResponseEntity.ok(response);
        } catch (ApiResponseException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getBody());
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
            UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();

            UploadCompletionResponse response = directUploadService.completeDirectUpload(
                    eventId,
                    objectKey,
                    filename,
                    contentType,
                    fileSize,
                    instagramHandle,
                    description,
                    anon,
                    expectedSha256,
                    user.getId());

            return ResponseEntity.ok(response);
        } catch (ApiResponseException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getBody());
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

}
