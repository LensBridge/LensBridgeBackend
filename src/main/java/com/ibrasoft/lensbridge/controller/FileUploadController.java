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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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

    private static final String SHA256_HEX = "^[0-9a-fA-F]{64}$";
    /** The uploads table keeps these in varchar(255) columns. */
    private static final int MAX_TEXT_LENGTH = 255;

    /**
     * Owner or moderator only. Anyone else gets the same 404 as for an upload that does not
     * exist (or was deleted), so the endpoint cannot be used to probe for upload ids.
     */
    @GetMapping("/{uploadId}")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UploadDto> getUploadById(@PathVariable UUID uploadId, @CurrentUser User user,
            Authentication authentication) {
        return uploadService.getUploadByIdAsDto(uploadId, user.getId(), isModerator(authentication))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Moderators see every upload for the event; everyone else only their own. */
    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<Page<UploadDto>> getUploadsByEvent(@PathVariable UUID eventId, @ParameterObject Pageable pageable,
            @CurrentUser User user, Authentication authentication) {
        return ResponseEntity.ok(uploadService.getUploadsByEventAsDto(
                eventId, user.getId(), isModerator(authentication), pageable));
    }

    @PostMapping("/{eventId}/direct/presign")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<PresignedUploadResponse> generatePresignedUploadUrl(
            @PathVariable UUID eventId,
            @RequestParam @Size(max = MAX_TEXT_LENGTH) String filename,
            @RequestParam String contentType,
            @RequestParam @Positive long fileSize,
            @RequestParam @Pattern(regexp = SHA256_HEX) String expectedSha256,
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
            @RequestParam @Size(max = MAX_TEXT_LENGTH) String filename,
            @RequestParam String contentType,
            @RequestParam @Positive long fileSize,
            @RequestParam(required = false) @Size(max = MAX_TEXT_LENGTH) String instagramHandle,
            @RequestParam(required = false) @Size(max = MAX_TEXT_LENGTH) String description,
            @RequestParam(defaultValue = "false") boolean anon,
            @RequestParam @Pattern(regexp = SHA256_HEX) String expectedSha256,
            @CurrentUser User user,
            Authentication authentication) {
        Role role = uploadLimitsService.getHighestRole(authentication);
        return ResponseEntity.ok(uploadWorkflowService.completeUpload(
                eventId, objectKey, filename, contentType, fileSize,
                instagramHandle, description, anon, expectedSha256, user.getId(), role));
    }

    @GetMapping("/limits")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UploadLimitsResponse> getUploadLimits(@CurrentUser User user, Authentication authentication) {
        Role role = uploadLimitsService.getHighestRole(authentication);
        return ResponseEntity.ok(uploadLimitsService.getLimitsForRole(role, user.getId()));
    }

    /** The same test the admin controller applies: the legacy ADMIN role or the moderate permission. */
    private static boolean isModerator(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(Role.ADMIN.getAuthority())
                        || a.equals(Permission.Authority.MEDIA_UPLOAD_MODERATE));
    }
}
