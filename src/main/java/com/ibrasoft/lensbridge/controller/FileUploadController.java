package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.Upload;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final UploadRepository uploadRepository;

    @PostMapping("/{eventId}")
    public ResponseEntity<?> uploadFile(@PathVariable String eventId,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam("instagramHandle") String instagramHandle) {
        try {
            // Generate a unique UUID for the upload
            UUID uuid = UUID.randomUUID();
            // Upload file to OneDrive
//            String fileUrl = this.uploadRepository.uploadFile(file, eventId);

            // Save metadata to MongoDB
            Upload upload = new Upload();
            upload.setUuid(uuid);
            upload.setFileName(file.getOriginalFilename());
//            upload.setFileUrl(fileUrl);
            upload.setInstagramHandle(instagramHandle);
            upload.setEventId(eventId);
            uploadRepository.save(upload);

            return ResponseEntity.ok("File uploaded successfully with UUID: " + uuid);
        } catch(Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading file");
        }
    }
}
