package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.response.AdminUploadDto;
import com.ibrasoft.lensbridge.dto.response.UserStatsResponse;
import com.ibrasoft.lensbridge.dto.response.GalleryItemDto;
import com.ibrasoft.lensbridge.exception.FileProcessingException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final R2StorageService r2StorageService;
    private final MediaConversionService mediaConversionService;

    @Value("${uploads.max-size}")
    private long maxUploadSize;

    @Value("${uploads.allowed-file-types}")
    private List<String> allowedFileTypes;

    @Value("${uploads.default-approved:false}")
    private boolean defaultApproved;

    @Value("${uploads.default-featured:false}")
    private boolean defaultFeatured;

    public void approveUpload(UUID uploadId) {
        Optional<Upload> uploadOpt = uploadRepository.findById(uploadId);
        if (uploadOpt.isPresent()) {
            Upload upload = uploadOpt.get();
            upload.setApproved(true);
            uploadRepository.save(upload);
            log.info("Upload {} approved successfully", uploadId);
        } else {
            log.warn("Attempted to approve non-existent upload: {}", uploadId);
            throw new IllegalArgumentException("Upload not found");
        }
    }

    public void featureUpload(UUID uploadId) {
        Optional<Upload> uploadOpt = uploadRepository.findById(uploadId);
        if (uploadOpt.isPresent()) {
            Upload upload = uploadOpt.get();
            upload.setFeatured(true);
            uploadRepository.save(upload);
            log.info("Upload {} featured successfully", uploadId);
        } else {
            log.warn("Attempted to feature non-existent upload: {}", uploadId);
            throw new IllegalArgumentException("Upload not found");
        }
    }

    public void unfeatureUpload(UUID uploadId) {
        Optional<Upload> uploadOpt = uploadRepository.findById(uploadId);
        if (uploadOpt.isPresent()) {
            Upload upload = uploadOpt.get();
            upload.setFeatured(false);
            uploadRepository.save(upload);
            log.info("Upload {} unfeatured successfully", uploadId);
        } else {
            log.warn("Attempted to unfeature non-existent upload: {}", uploadId);
            throw new IllegalArgumentException("Upload not found");
        }
    }

    public void unapproveUpload(UUID uploadId) {
        Optional<Upload> uploadOpt = uploadRepository.findById(uploadId);
        if (uploadOpt.isPresent()) {
            Upload upload = uploadOpt.get();
            upload.setApproved(false);
            uploadRepository.save(upload);
            log.info("Upload {} unapproved successfully", uploadId);
        } else {
            log.warn("Attempted to unapprove non-existent upload: {}", uploadId);
            throw new IllegalArgumentException("Upload not found");
        }
    }

    /**
     * Count uploads for a user today
     */
    public long countUploadsToday(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        return uploadRepository.countByUploadedByAndCreatedDateBetween(
                userId,
                startOfDay,
                endOfDay);
    }

    /**
     * Check if user has reached their daily upload limit
     */
    public boolean hasReachedDailyLimit(UUID userId, int dailyLimit) {
        return countUploadsToday(userId) >= dailyLimit;
    }

    public Upload createUpload(MultipartFile file, UUID eventId, String description, String instagramHandle,
            boolean anon, UUID uploadedBy) {
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Empty file cannot be uploaded");
            }
            if (file.getSize() > maxUploadSize) {
                throw new IllegalArgumentException(
                        "File size exceeds the maximum limit of " + maxUploadSize + " bytes");
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

                outputFile = File.createTempFile("upload_", "_" + originalFilename);
                file.transferTo(outputFile);
                fileURL = r2StorageService.uploadImage(outputFile, UUID.randomUUID().toString());
                outputFile.delete();
            } else if (contentType != null && contentType.startsWith("video")) {
                uploadType = UploadType.VIDEO;
                outputFile = File.createTempFile("upload_", "_" + originalFilename);
                file.transferTo(outputFile);
                fileURL = r2StorageService.uploadVideo(outputFile, UUID.randomUUID().toString());
                outputFile.delete();
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + contentType);
            }

            UUID uuid = UUID.randomUUID();
            Upload upload = new Upload(uuid, originalFilename, fileURL, null, description, instagramHandle, uploadedBy,
                    eventId, LocalDateTime.now(), defaultApproved, defaultFeatured, anon, uploadType);
            uploadRepository.save(upload);
            return upload;
        } catch (Exception e) {
            log.error("Failed to process file for upload: {}", e.getMessage());
            throw new FileProcessingException("Failed to process file for upload");
        }
    }

    /**
     * Create an Upload entity for a file that has been directly uploaded to R2.
     * This method is used when files are uploaded via presigned URLs.
     */
    public Upload createDirectUpload(String objectKey, String fileName, String contentType,
            UUID eventId, String description, String instagramHandle,
            boolean anon, UUID uploadedBy) {
        try {
            // Determine upload type from content type
            UploadType uploadType;
            if (contentType != null && contentType.startsWith("image")) {
                uploadType = UploadType.IMAGE;
            } else if (contentType != null && contentType.startsWith("video")) {
                uploadType = UploadType.VIDEO;
            } else {
                uploadType = UploadType.IMAGE; // Default fallback
            }

            // Create Upload entity
            UUID uuid = UUID.randomUUID();
            Upload upload = new Upload(
                    uuid,
                    fileName,
                    objectKey,
                    null, // thumbnailUrl - will be set async by ThumbnailService
                    description,
                    instagramHandle,
                    uploadedBy,
                    eventId,
                    LocalDateTime.now(),
                    defaultApproved,
                    defaultFeatured,
                    anon,
                    uploadType);

            uploadRepository.save(upload);
            log.info("Created direct upload record: {} for object: {}", uuid, objectKey);
            return upload;

        } catch (Exception e) {
            log.error("Failed to create direct upload record for object: {}", objectKey, e);
            throw new FileProcessingException("Failed to create upload record for direct upload");
        }
    }

    public Page<Upload> getAllUploads(Pageable pageable) {
        return uploadRepository.findAll(pageable);
    }

    public Page<Upload> getUploadsByEvent(UUID eventId, Pageable pageable) {
        return uploadRepository.findByEventId(eventId, pageable);
    }

    public Optional<Upload> getUploadById(UUID id) {
        return uploadRepository.findById(id);
    }

    public Upload updateUpload(Upload upload) {
        return uploadRepository.save(upload);
    }

    public void deleteUpload(UUID id) {
        Optional<Upload> uploadOpt = uploadRepository.findById(id);
        if (uploadOpt.isPresent()) {
            Upload upload = uploadOpt.get();
            try {
                // Delete original file from R2 storage
                String objectKey = r2StorageService.extractObjectKeyFromUrl(upload.getFileUrl());
                if (objectKey != null) {
                    r2StorageService.deleteObject(objectKey);
                }
                // Delete thumbnail from R2 storage if exists
                String thumbnailKey = upload.getThumbnailUrl();
                if (thumbnailKey != null && !thumbnailKey.isBlank()) {
                    r2StorageService.deleteObject(thumbnailKey);
                }
            } catch (Exception e) {
                log.warn("Failed to delete file from R2 storage for upload {}: {}", id, e.getMessage());
                // Continue with database deletion even if R2 deletion fails
            }
        }
        uploadRepository.deleteById(id);
    }

    /**
     * Delete user's own upload with ownership validation
     */
    public void deleteUserUpload(UUID uploadId, UUID userId) {
        log.info("User {} attempting to delete upload {}", userId, uploadId);

        Optional<Upload> uploadOpt = uploadRepository.findById(uploadId);
        if (uploadOpt.isEmpty()) {
            throw new IllegalArgumentException("Upload not found");
        }

        Upload upload = uploadOpt.get();

        // Verify ownership
        if (!upload.getUploadedBy().equals(userId)) {
            throw new SecurityException("You can only delete your own uploads");
        }

        try {
            // Delete original file from R2 storage
            String objectKey = r2StorageService.extractObjectKeyFromUrl(upload.getFileUrl());
            if (objectKey != null) {
                r2StorageService.deleteObject(objectKey);
            }
            // Delete thumbnail from R2 storage if exists
            String thumbnailKey = upload.getThumbnailUrl();
            if (thumbnailKey != null && !thumbnailKey.isBlank()) {
                r2StorageService.deleteObject(thumbnailKey);
            }
        } catch (Exception e) {
            log.warn("Failed to delete file from R2 storage for upload {}: {}", uploadId, e.getMessage());
            // Continue with database deletion even if R2 deletion fails
        }

        // Delete the upload from database
        uploadRepository.deleteById(uploadId);
        log.info("Upload {} deleted successfully by user {}", uploadId, userId);
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
     * Get user upload statistics
     */
    public UserStatsResponse getUserStats(UUID userId) {
        long totalUploads = uploadRepository.countByUploadedBy(userId);
        long approvedUploads = uploadRepository.countByUploadedByAndApproved(userId, true);
        long featuredUploads = uploadRepository.countByUploadedByAndFeatured(userId, true);
        long pendingUploads = totalUploads - approvedUploads;

        return new UserStatsResponse(
                (int) totalUploads,
                (int) approvedUploads,
                (int) featuredUploads,
                (int) pendingUploads);
    }

    /**
     * Get user uploads as GalleryItemDTOs (user can see their own uploads
     * regardless of approval status)
     */
    public Page<GalleryItemDto> getUserUploadsAsGalleryItems(UUID userId, Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findByUploadedBy(userId, pageable);
        return uploads.map(upload -> convertToUserGalleryItem(upload, userId));
    }

    /**
     * Convert Upload to GalleryItemDto for user's own uploads (can see all their
     * own content)
     */
    private GalleryItemDto convertToUserGalleryItem(Upload upload, UUID userId) {
        // Verify the user owns this upload
        if (!upload.getUploadedBy().equals(userId)) {
            throw new SecurityException("User can only access their own uploads");
        }

        GalleryItemDto item = new GalleryItemDto();

        // Basic info
        item.setId(upload.getUuid().toString());
        item.setTitle(upload.getUploadDescription() != null ? upload.getUploadDescription() : "Untitled");
        item.setFeatured(upload.isFeatured());
        item.setType(upload.getContentType().toString().toLowerCase());

        // Generate secure URL (user can see their own content regardless of approval)
        try {
            String objectKey = r2StorageService.extractObjectKeyFromUrl(upload.getFileUrl());
            String secureUrl = r2StorageService.getSecureUrl(objectKey, true, false); // true for approved access, false
                                                                                      // for not admin
            item.setSrc(secureUrl);
        } catch (Exception e) {
            log.error("Failed to generate secure URL for user upload {}: {}", upload.getUuid(), e.getMessage());
            item.setSrc(null);
        }

        // Generate secure thumbnail
        try {
            String objectKey = r2StorageService.extractObjectKeyFromUrl(upload.getFileUrl());
            String thumbnailUrl = r2StorageService.getSecureThumbnailUrl(objectKey, true, false);
            item.setThumbnail(thumbnailUrl);
        } catch (Exception e) {
            log.error("Failed to generate thumbnail for user upload {}: {}", upload.getUuid(), e.getMessage());
            item.setThumbnail(null);
        }

        // Set author (always the user's name for their own uploads, even if marked
        // anonymous)
        Optional<User> userOpt = userService.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            item.setAuthor(user.getFirstName() + " " + user.getLastName());
        } else {
            item.setAuthor("You");
        }

        // Event information
        if (upload.getEventId() != null) {
            Optional<Event> eventOpt = eventsService.getEventById(upload.getEventId());
            item.setEvent(eventOpt.map(Event::getName).orElse("General"));
        } else {
            item.setEvent("General");
        }

        // Format date
        if (upload.getCreatedDate() != null) {
            item.setDate(upload.getCreatedDate().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        } else {
            item.setDate("Unknown");
        }

        return item;
    }

    /**
     * Convert Upload entity to AdminUploadDto with user information and secure URLs
     * populated.
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
            String objectKey = r2StorageService.extractObjectKeyFromUrl(upload.getFileUrl());
            String secureUrl = r2StorageService.getSecureUrl(objectKey, upload.isApproved(), true);
            dto.setSecureUrl(secureUrl);

            // Generate secure thumbnail URL using the stored thumbnail key
            String thumbnailKey = upload.getThumbnailUrl();
            if (thumbnailKey != null && !thumbnailKey.isBlank()) {
                String thumbnailUrl = r2StorageService.getSecureThumbnailUrl(thumbnailKey, upload.isApproved(), true);
                dto.setThumbnailUrl(thumbnailUrl);
            } else {
                // Fallback: use original image URL as thumbnail if no thumbnail generated yet
                dto.setThumbnailUrl(secureUrl);
            }

        } catch (Exception e) {
            // If secure URL generation fails, log error but don't break the DTO creation
            log.error("Failed to generate secure URLs for upload {}: {}", upload.getUuid(), e.getMessage());
            dto.setSecureUrl(null);
            dto.setThumbnailUrl(null);
        }

        Optional<User> userOpt = userService.findById(upload.getUploadedBy());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            dto.setUploaderFirstName(user.getFirstName());
            dto.setUploaderLastName(user.getLastName());
            dto.setUploaderEmail(user.getEmail());
            dto.setUploaderStudentNumber(user.getStudentNumber());
        }

        // Fetch and populate event information
        if (upload.getEventId() != null) {
            Optional<Event> eventOpt = eventsService.getEventById(upload.getEventId());
            eventOpt.ifPresent(event -> dto.setEventName(event.getName()));
        }

        return dto;
    }
}
