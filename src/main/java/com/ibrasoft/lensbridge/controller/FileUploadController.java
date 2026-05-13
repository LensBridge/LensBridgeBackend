package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.upload.response.PresignedUploadResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadCompletionResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
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

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final UploadService uploadService;
    private final UploadWorkflowService uploadWorkflowService;
    private final UploadLimitsService uploadLimitsService;

    @GetMapping("/{uploadId}")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UploadDto> getUploadById(@PathVariable UUID uploadId) {
        return uploadService.getUploadByIdAsDto(uploadId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<Page<UploadDto>> getUploadsByEvent(@PathVariable UUID eventId, Pageable pageable) {
        return ResponseEntity.ok(uploadService.getUploadsByEventAsDto(eventId, pageable));
    }

    @PostMapping("/{eventId}/direct/presign")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<PresignedUploadResponse> generatePresignedUploadUrl(
            @PathVariable UUID eventId,
            @RequestParam String filename,
            @RequestParam String contentType,
            @RequestParam long fileSize,
            @RequestParam String expectedSha256,
            @CurrentUser User user,
            Authentication authentication) {
        Role role = uploadLimitsService.getHighestRole(authentication);
        return ResponseEntity.ok(uploadWorkflowService.initiateUpload(
                eventId, filename, contentType, fileSize, expectedSha256, user.getId(), role));
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
    public ResponseEntity<?> getUploadLimits(@CurrentUser User user, Authentication authentication) {
        Role role = uploadLimitsService.getHighestRole(authentication);
        return ResponseEntity.ok(uploadLimitsService.getLimitsForRole(role, user.getId()));
    }
}
