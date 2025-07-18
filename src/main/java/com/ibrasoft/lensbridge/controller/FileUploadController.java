package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.upload.UploadType;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.CloudinaryService;
import com.ibrasoft.lensbridge.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final UploadService uploadService;
    private final CloudinaryService cloudinaryService;

    @PostMapping("/{eventId}/batch")
    @PreAuthorize("hasRole('" + Role.VERIFIED + "')")
    public ResponseEntity<?> uploadFiles(@PathVariable UUID eventId,
                                         @RequestParam("files") List<MultipartFile> files,
                                         @RequestParam(value = "instagramHandle", required = false) String instagramHandle,
                                         @RequestParam(value = "description", required = false) String description,
                                         @RequestParam(value="anon") boolean anon) {
        try {
            List<UUID> uploadedUuids = new ArrayList<>();
            UserDetailsImpl user = (UserDetailsImpl) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            for (MultipartFile file : files) {
                UUID uuid = UUID.randomUUID();
                Upload upload = new Upload();
                if (file.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty: " + file.getOriginalFilename());
                }
                if (file.getSize() > 100 * 1024 * 1024) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File size exceeds limit: " + file.getOriginalFilename());
                }
                
                String contentType = file.getContentType();
                if (contentType == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unable to determine file type: " + file.getOriginalFilename());
                }
                
                String fileUrl;
                if (contentType.contains("image") || contentType.contains("octet-stream")) {
                    upload.setContentType(UploadType.IMAGE);
                    fileUrl = cloudinaryService.uploadImage(file.getBytes(), uuid.toString());
                } else if (contentType.contains("video")) {
                    upload.setContentType(UploadType.VIDEO);
                    fileUrl = cloudinaryService.uploadVideo(file.getBytes(), uuid.toString());
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported file type: " + contentType);
                }

                upload.setUuid(uuid);
                upload.setFileName(file.getOriginalFilename());
                upload.setFileUrl(fileUrl);
                upload.setInstagramHandle(instagramHandle);
                upload.setEventId(eventId);
                upload.setAnon(anon);
                upload.setApproved(false);
                upload.setUploadedBy(user.getId());
                upload.setFeatured(false);
                upload.setCreatedDate(LocalDate.now());
                upload.setUploadDescription(description);

                uploadService.createUpload(upload);
                uploadedUuids.add(uuid);
            }

            return ResponseEntity.ok("Files uploaded successfully. UUIDs: " + uploadedUuids);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading files: " + e.getMessage());
        }
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
}
