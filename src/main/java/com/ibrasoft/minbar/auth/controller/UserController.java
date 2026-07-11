package com.ibrasoft.minbar.auth.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ibrasoft.minbar.auth.dto.request.UpdateProfileRequest;
import com.ibrasoft.minbar.auth.dto.response.MessageResponse;
import com.ibrasoft.minbar.auth.dto.response.UserInfoResponse;
import com.ibrasoft.minbar.auth.dto.response.UserStatsResponse;
import com.ibrasoft.minbar.media.dto.response.GalleryItemDto;
import com.ibrasoft.minbar.auth.model.Role;
import com.ibrasoft.minbar.auth.model.User;
import com.ibrasoft.minbar.shared.security.CurrentUser;
import com.ibrasoft.minbar.media.service.GalleryService;
import com.ibrasoft.minbar.media.service.UploadService;
import com.ibrasoft.minbar.auth.service.UserService;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final UploadService uploadService;
    private final GalleryService galleryService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UserInfoResponse> getUserProfile(@CurrentUser User user) {
        return ResponseEntity.ok(new UserInfoResponse(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getStudentNumber(), user.isVerified(), user.getRoles()));
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UserInfoResponse> updateUserProfile(
            @Valid @RequestBody UpdateProfileRequest updateRequest,
            @CurrentUser User user) {
        User updated = userService.updateProfile(user.getId(), updateRequest);
        return ResponseEntity.ok(new UserInfoResponse(
                updated.getId(), updated.getFirstName(), updated.getLastName(),
                updated.getEmail(), updated.getStudentNumber(), updated.isVerified(), updated.getRoles()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<UserStatsResponse> getUserStats(@CurrentUser User user) {
        return ResponseEntity.ok(uploadService.getUserStats(user.getId()));
    }

    @GetMapping("/uploads")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<Page<GalleryItemDto>> getUserUploads(Pageable pageable, @CurrentUser User user) {
        return ResponseEntity.ok(galleryService.getUserGallery(user.getId(), pageable));
    }

    @DeleteMapping("/uploads/{uploadId}")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<MessageResponse> deleteUserUpload(
            @PathVariable UUID uploadId,
            @CurrentUser User user) {
        uploadService.deleteUserUpload(uploadId, user.getId());
        return ResponseEntity.ok(new MessageResponse("Upload deleted successfully"));
    }
}
