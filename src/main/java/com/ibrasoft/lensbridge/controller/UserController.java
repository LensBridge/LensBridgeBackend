package com.ibrasoft.lensbridge.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ibrasoft.lensbridge.dto.auth.request.UpdateProfileRequest;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.auth.response.UserInfoResponse;
import com.ibrasoft.lensbridge.dto.auth.response.UserStatsResponse;
import com.ibrasoft.lensbridge.dto.upload.response.GalleryItemDto;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.security.CurrentUser;
import com.ibrasoft.lensbridge.service.GalleryService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UserService;

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
