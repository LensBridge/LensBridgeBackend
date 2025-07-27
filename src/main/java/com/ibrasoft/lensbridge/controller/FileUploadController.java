package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final UploadService uploadService;

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
}
