package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.response.GalleryItemDto;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import com.ibrasoft.lensbridge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

    private final UploadRepository uploadRepository;
    private final UserRepository uploaderRepository;
    private final EventsRepository eventsRepository;
    private final CloudinaryService cloudinaryService;

    /**
     * Get all approved gallery items with secure URLs for public access.
     * Only approved content is returned with time-limited signed URLs.
     */
    public Page<GalleryItemDto> getAllApprovedGalleryItems(Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findByApprovedTrue(pageable);
        return uploads.map(this::convertToPublicGalleryItem); // Use secure URLs for public
    }

    /**
     * Get all gallery items (admin only) with secure URLs.
     * This method should only be called by admin users.
     */
    public Page<GalleryItemDto> getAllGalleryItems(Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findAll(pageable);
        return uploads.map(this::convertToAdminGalleryItem); // Admin can see all with secure URLs
    }

    /**
     * Get gallery items by event with secure URLs (public access, approved only).
     */
    public Page<GalleryItemDto> getGalleryItemsByEvent(UUID eventId, Pageable pageable) {
        // For public event galleries, only return approved content
        Page<Upload> uploads = uploadRepository.findByEventId(eventId, pageable);
        return uploads.map(this::convertToPublicGalleryItem);
    }

    /**
     * Convert upload to gallery item for public access (approved content only).
     */
    private GalleryItemDto convertToPublicGalleryItem(Upload upload) {
        if (!upload.isApproved()) {
            throw new SecurityException("Cannot generate public gallery item for unapproved content");
        }
        return convertToGalleryItem(upload, false); // false = not admin
    }

    /**
     * Convert upload to gallery item for admin access (can see all content).
     */
    private GalleryItemDto convertToAdminGalleryItem(Upload upload) {
        return convertToGalleryItem(upload, true); // true = admin
    }

    /**
     * Convert upload to gallery item with secure URLs.
     * @param upload The upload to convert
     * @param isAdmin Whether the requesting user is an admin
     * @return GalleryItemDto with secure URLs
     */
    private GalleryItemDto convertToGalleryItem(Upload upload, boolean isAdmin) {
        GalleryItemDto item = new GalleryItemDto();
        User uploader = uploaderRepository.findById(upload.getUploadedBy())
                .orElse(null);
        
        // Basic info
        item.setId(upload.getUuid().toString());
        
        // Generate secure URL instead of using direct Cloudinary URL
        try {
            String secureUrl = cloudinaryService.getSecureUrl(upload.getFileUrl(), upload.isApproved(), isAdmin);
            item.setSrc(secureUrl);
        } catch (SecurityException e) {
            log.warn("Access denied for upload {}: {}", upload.getUuid(), e.getMessage());
            item.setSrc(null); // Don't provide URL if access is denied
        } catch (Exception e) {
            log.error("Failed to generate secure URL for upload {}: {}", upload.getUuid(), e.getMessage());
            item.setSrc(null); // Don't provide URL if generation fails
        }
        
        item.setTitle(upload.getUploadDescription() != null ? upload.getUploadDescription() : "Untitled");
        item.setFeatured(upload.isFeatured());
        item.setType(upload.getContentType().toString().toLowerCase());

        if (upload.isAnon()) {
            item.setAuthor("Anonymous");
        } else if (uploader != null) {
            item.setAuthor(uploader.getFirstName() + " " + uploader.getLastName());
        } else {
            item.setAuthor("Unknown");
        }

        // Generate secure thumbnail
        String thumbnail = generateSecureThumbnail(upload.getFileUrl(), upload.getContentType().toString().toLowerCase(), upload.isApproved(), isAdmin);
        item.setThumbnail(thumbnail);
        
        String eventName = getEventName(upload.getEventId());
        item.setEvent(eventName);
        
        // Format date
        String date = formatDate(upload);
        item.setDate(date);
        
        return item;
    }

    /**
     * Generate secure thumbnail URL with access control.
     */
    private String generateSecureThumbnail(String fileUrl, String contentType, boolean isApproved, boolean isAdmin) {
        if (fileUrl == null) return null;
        
        try {
            return cloudinaryService.getSecureThumbnailUrl(fileUrl, isApproved, isAdmin);
        } catch (SecurityException e) {
            log.warn("Access denied for thumbnail: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to generate secure thumbnail for URL {}: {}", fileUrl, e.getMessage());
            return null;
        }
    }

    private String getEventName(UUID eventId) {
        if (eventId == null) return "General";
        
        return eventsRepository.findEventById(eventId)
                .map(Event::getName)
                .orElse("General");
    }

    private String formatDate(Upload upload) {
        if (upload.getCreatedDate() != null) {
            return upload.getCreatedDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return "Unknown";
    }
}
