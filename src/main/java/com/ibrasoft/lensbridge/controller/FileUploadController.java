package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.Upload;
import com.ibrasoft.lensbridge.service.CloudinaryService;
import com.ibrasoft.lensbridge.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    private CloudinaryService cloudinaryService;

    @PostMapping("/{eventId}/batch")
    public ResponseEntity<?> uploadFiles(@PathVariable UUID eventId, @RequestParam("files") List<MultipartFile> files, @RequestParam(value = "instagramHandle", required = false) String instagramHandle,
                                         @RequestParam(value = "description", required = false) String description) {
        try {
            List<UUID> uploadedUuids = new ArrayList<>();

            for (MultipartFile file : files) {
                UUID uuid = UUID.randomUUID();
                if (file.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty: " + file.getOriginalFilename());
                }
                if (file.getSize() > 100 * 1024 * 1024) { // 10 MB limit
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File size exceeds limit: " + file.getOriginalFilename());
                }
                String fileUrl;
                if (file.getContentType().contains("image")){
                    fileUrl = cloudinaryService.uploadImage(file.getBytes(), uuid.toString());
                }
                else if (file.getContentType().contains("video")){
                    fileUrl = cloudinaryService.uploadVideo(file.getBytes(), uuid.toString());
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported file type: " + file.getContentType());
                }

                Upload upload = new Upload();
                upload.setUuid(uuid);
                upload.setFileName(file.getOriginalFilename());
                upload.setFileUrl(fileUrl);
                upload.setInstagramHandle(instagramHandle != null ? instagramHandle : "");
                upload.setEventId(eventId);
                upload.setCreatedDate(LocalDate.now());
                upload.setUploadDescription(description.isEmpty() ? "" : description);

                uploadService.createUpload(upload);
                uploadedUuids.add(uuid);
            }

            return ResponseEntity.ok("Files uploaded successfully. UUIDs: " + uploadedUuids);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading files: " + e.getMessage());
        }
    }


    @GetMapping("/event/{eventId}")
    public ResponseEntity<?> getUploadsByEvent(@PathVariable UUID eventId) {
        try {
            return ResponseEntity.ok(uploadService.getUploadsByEventId(eventId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching uploads: " + e.getMessage());
        }
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<?> getUploadById(@PathVariable UUID uploadId) {
        try {
            return uploadService.getUploadById(uploadId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching upload: " + e.getMessage());
        }
    }
}
