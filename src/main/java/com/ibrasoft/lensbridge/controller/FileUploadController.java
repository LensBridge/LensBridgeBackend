package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.upload.response.PresignedUploadResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadCompletionResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
import com.ibrasoft.lensbridge.dto.upload.response.UploadLimitsResponse;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.security.CurrentUser;
import com.ibrasoft.lensbridge.service.UploadLimitsService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UploadWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final UploadService uploadService;
    private final UploadWorkflowService uploadWorkflowService;
    private final UploadLimitsService uploadLimitsService;

    /**
     * A caller who neither uploaded the file nor holds {@code media:upload:read} gets 404 for
     * anything not yet approved. 404 rather than 403 on purpose: 403 would confirm the id exists.
     */
    @GetMapping("/{uploadId}")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UploadDto> getUploadById(@PathVariable UUID uploadId,
                                                   @CurrentUser User user,
                                                   Authentication authentication) {
        return uploadService.getUploadByIdAsDto(uploadId, user.getId(), canReadAllUploads(authentication))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Ordinary callers see approved uploads plus their own; moderators see the full queue. */
    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<Page<UploadDto>> getUploadsByEvent(@PathVariable UUID eventId,
                                                             @ParameterObject Pageable pageable,
                                                             @CurrentUser User user,
                                                             Authentication authentication) {
        return ResponseEntity.ok(uploadService.getUploadsByEventAsDto(
                eventId, pageable, user.getId(), canReadAllUploads(authentication)));
    }

    private static boolean canReadAllUploads(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> Permission.Authority.MEDIA_UPLOAD_READ.equals(granted.getAuthority()));
    }

    @PostMapping("/{eventId}/direct/presign")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<PresignedUploadResponse> generatePresignedUploadUrl(
            @PathVariable UUID eventId,
            @RequestParam String filename,
            @RequestParam String contentType,
            @RequestParam long fileSize,
            @RequestParam String expectedSha256,
            @CurrentUser User user) {
        return ResponseEntity.ok(uploadWorkflowService.initiateUpload(
                eventId, filename, contentType, fileSize, expectedSha256, user.getId(), user));
    }

    @PostMapping("/{eventId}/direct/complete")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UploadCompletionResponse> completeDirectUpload(
            @PathVariable UUID eventId,
            @RequestParam String objectKey,
            @RequestParam String filename,
            @RequestParam String contentType,
            @RequestParam long fileSize,
            @RequestParam(required = false) String instagramHandle,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean anon,
            @RequestParam String expectedSha256,
            @CurrentUser User user) {
        return ResponseEntity.ok(uploadWorkflowService.completeUpload(
                eventId, objectKey, filename, contentType, fileSize,
                instagramHandle, description, anon, expectedSha256, user.getId()));
    }

    @GetMapping("/limits")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UploadLimitsResponse> getUploadLimits(@CurrentUser User user) {
        return ResponseEntity.ok(uploadLimitsService.getLimitsForUser(user));
    }
}
