package com.ibrasoft.lensbridge.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibrasoft.lensbridge.dto.request.UpdateProfileRequest;
import com.ibrasoft.lensbridge.dto.response.GalleryItemDto;
import com.ibrasoft.lensbridge.dto.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.response.UserInfoResponse;
import com.ibrasoft.lensbridge.dto.response.UserStatsResponse;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UserService;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    UserService userService;
    
    @Autowired
    UploadService uploadService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUserProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                .body(new MessageResponse("Authentication required"));
        }

        try {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            User user = userService.findById(userDetails.getId()).orElse(null);
            
            if (user == null) {
                return ResponseEntity.status(404)
                    .body(new MessageResponse("User not found"));
            }
            
            UserInfoResponse response = new UserInfoResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.isVerified(),
                user.getRoles()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving user profile: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new MessageResponse("Error retrieving profile"));
        }
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> updateUserProfile(@Valid @RequestBody UpdateProfileRequest updateRequest,
                                               Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                .body(new MessageResponse("Authentication required"));
        }

        try {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            User updatedUser = userService.updateProfile(userDetails.getId(), updateRequest);
            
            UserInfoResponse response = new UserInfoResponse(
                updatedUser.getId(),
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getEmail(),
                updatedUser.isVerified(),
                updatedUser.getRoles()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating user profile: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new MessageResponse("Error updating profile"));
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUserStats(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                .body(new MessageResponse("Authentication required"));
        }

        try {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            UserStatsResponse stats = uploadService.getUserStats(userDetails.getId());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error retrieving user stats: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new MessageResponse("Error retrieving stats"));
        }
    }

    @GetMapping("/uploads")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> getUserUploads(Pageable pageable, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                .body(new MessageResponse("Authentication required"));
        }

        try {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            Page<GalleryItemDto> uploads = uploadService.getUserUploadsAsGalleryItems(userDetails.getId(), pageable);
            
            return ResponseEntity.ok(uploads);
            
        } catch (Exception e) {
            log.error("Error retrieving user uploads: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new MessageResponse("Error retrieving uploads"));
        }
    }

    @DeleteMapping("/uploads/{uploadId}")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> deleteUserUpload(@PathVariable UUID uploadId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                .body(new MessageResponse("Authentication required"));
        }

        try {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            uploadService.deleteUserUpload(uploadId, userDetails.getId());
            
            return ResponseEntity.ok(new MessageResponse("Upload deleted successfully"));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting user upload {}: {}", uploadId, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new MessageResponse("Error deleting upload"));
        }
    }
}
