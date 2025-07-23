package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.response.AdminUploadDto;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.upload.UploadType;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final UploadRepository uploadRepository;
    private final UserService userService;
    private final EventsService eventsService;
    private final CloudinaryService cloudinaryService;
    private final MediaConversionService mediaConversionService;

    @Value("${uploads.max-size}")
    private long maxUploadSize;

    @Value("${uploads.allowed-file-types}")
    private List<String> allowedFileTypes;

    @Value("${uploads.default-approved:false}")
    private boolean defaultApproved;

    @Value("${uploads.default-featured:false}")
    private boolean defaultFeatured;

    public Upload createUpload(MultipartFile file, UUID eventId, String description, String instagramHandle,
            boolean anon, UUID uploadedBy) throws Exception {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Empty file cannot be uploaded");
        }
        if (file.getSize() > maxUploadSize) {
            throw new IllegalArgumentException("File size exceeds the maximum limit of " + maxUploadSize + " bytes");
        }
        if (file.getContentType() == null || !allowedFileTypes.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported file type: " + file.getContentType());
        }

        String fileURL;

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        File outputFile;
        UploadType uploadType;

        if (contentType != null && contentType.startsWith("image")) {
            uploadType = UploadType.IMAGE;
            // Handle HEIC conversion to JPEG
            // if ("image/heic".equals(contentType)) {
            //     outputFile = mediaConversionService.convertHEICToJPEG(file.getInputStream(), originalFilename);
            // } else {
                outputFile = File.createTempFile("upload_", "_" + originalFilename);
                file.transferTo(outputFile);
            // }
            fileURL = cloudinaryService.uploadImage(outputFile, UUID.randomUUID().toString());
            outputFile.delete(); 
        } else if (contentType != null && contentType.startsWith("video")) {
            uploadType = UploadType.VIDEO;
            outputFile = File.createTempFile("upload_", "_" + originalFilename);
            file.transferTo(outputFile);
            fileURL = cloudinaryService.uploadVideo(outputFile, UUID.randomUUID().toString());
            outputFile.delete(); 
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        UUID uuid = UUID.randomUUID();
        Upload upload = new Upload(uuid, originalFilename, fileURL, description, instagramHandle, uploadedBy, eventId,
                LocalDateTime.now(), defaultApproved, defaultFeatured, anon, uploadType);
        uploadRepository.save(upload);
        return upload;
    }

    public Page<Upload> getAllUploads(Pageable pageable) {
        return uploadRepository.findAll(pageable);
    }

    public Optional<Upload> getUploadById(UUID id) {
        return uploadRepository.findById(id);
    }

    public Upload updateUpload(Upload upload) {
        return uploadRepository.save(upload);
    }

    public void deleteUpload(UUID id) {
        uploadRepository.deleteById(id);
    }

    /**
     * Get all uploads with user information populated for admin interface.
     * This method fetches uploads and includes the uploader's name and details.
     */
    public Page<AdminUploadDto> getAllUploadsForAdmin(Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findAll(pageable);
        return uploads.map(this::convertToAdminUploadDto);
    }

    /**
     * Get uploads by approval status with user information for admin interface.
     */
    public Page<AdminUploadDto> getUploadsByApprovalStatus(boolean approved, Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findByApproved(approved, pageable);
        return uploads.map(this::convertToAdminUploadDto);
    }

    /**
     * Get uploads by featured status with user information for admin interface.
     */
    public Page<AdminUploadDto> getUploadsByFeaturedStatus(boolean featured, Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findByFeatured(featured, pageable);
        return uploads.map(this::convertToAdminUploadDto);
    }

    public Page<Upload> getUploadsByUploadedBy(UUID userId, Pageable pageable) {
        return uploadRepository.findByUploadedBy(userId, pageable);
    }

    /**
     * Convert Upload entity to AdminUploadDto with user information and secure URLs populated.
     * Generates time-limited signed URLs for admin access.
     */
    private AdminUploadDto convertToAdminUploadDto(Upload upload) {
        AdminUploadDto dto = new AdminUploadDto();

        // Copy upload fields
        dto.setUuid(upload.getUuid());
        dto.setFileName(upload.getFileName());
        dto.setFileUrl(upload.getFileUrl()); // Keep original URL for internal reference
        dto.setUploadDescription(upload.getUploadDescription());
        dto.setInstagramHandle(upload.getInstagramHandle());
        dto.setUploadedBy(upload.getUploadedBy());
        dto.setEventId(upload.getEventId());
        dto.setCreatedDate(upload.getCreatedDate());
        dto.setApproved(upload.isApproved());
        dto.setFeatured(upload.isFeatured());
        dto.setAnon(upload.isAnon());
        dto.setContentType(upload.getContentType());

        // Generate secure URLs for admin access
        try {
            // Admins can view both approved and unapproved content
            String secureUrl = cloudinaryService.getSecureUrl(upload.getFileUrl(), upload.isApproved(), true);
            dto.setSecureUrl(secureUrl);
            
            // Generate secure thumbnail URL
            String thumbnailUrl = cloudinaryService.getSecureThumbnailUrl(upload.getFileUrl(), upload.isApproved(), true);
            dto.setThumbnailUrl(thumbnailUrl);
            
        } catch (Exception e) {
            // If secure URL generation fails, log error but don't break the DTO creation
            log.error("Failed to generate secure URLs for upload {}: {}", upload.getUuid(), e.getMessage());
            dto.setSecureUrl(null);
            dto.setThumbnailUrl(null);
        }

        // Populate user information if not anonymous
        if (!upload.isAnon() && upload.getUploadedBy() != null) {
            Optional<User> userOpt = userService.findById(upload.getUploadedBy());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                dto.setUploaderFirstName(user.getFirstName());
                dto.setUploaderLastName(user.getLastName());
                dto.setUploaderEmail(user.getEmail());
                dto.setUploaderStudentNumber(user.getStudentNumber());
            }
        }

        // Fetch and populate event information
        if (upload.getEventId() != null) {
            Optional<Event> eventOpt = eventsService.getEventById(upload.getEventId());
            if (eventOpt.isPresent()) {
                dto.setEventName(eventOpt.get().getName());
            }
        }

        return dto;
    }
}
