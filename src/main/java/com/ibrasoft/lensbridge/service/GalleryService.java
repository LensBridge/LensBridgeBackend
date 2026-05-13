package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.GalleryItemDto;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.upload.EventsRepository;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

    private final UploadRepository uploadRepository;
    private final UserService userService;
    private final EventsRepository eventsRepository;
    private final R2StorageService r2StorageService;

    public Page<GalleryItemDto> getAllApprovedGalleryItems(Pageable pageable) {
        return uploadRepository.findByApprovedTrueAndDeletedAtIsNull(pageable)
                .map(u -> toGalleryItem(u, false));
    }

    public Page<GalleryItemDto> getAllGalleryItems(Pageable pageable) {
        return uploadRepository.findByDeletedAtIsNull(pageable)
                .map(u -> toGalleryItem(u, true));
    }

    public Page<GalleryItemDto> getGalleryItemsByEvent(UUID eventId, Pageable pageable) {
        MediaEvent mediaEvent = eventsRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        return uploadRepository.findByMediaEventAndApprovedTrueAndDeletedAtIsNull(mediaEvent, pageable)
                .map(u -> toGalleryItem(u, false));
    }

    public Page<GalleryItemDto> getUserGallery(UUID userId, Pageable pageable) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return uploadRepository.findByUploadedByAndDeletedAtIsNull(user, pageable)
                .map(upload -> {
                    if (upload.getUploadedBy() == null || !upload.getUploadedBy().getId().equals(userId)) {
                        throw new SecurityException("User can only access their own uploads");
                    }
                    GalleryItemDto item = toGalleryItem(upload, true);
                    // always show real name for own uploads regardless of anon flag
                    item.setAuthor(user.getFirstName() + " " + user.getLastName());
                    return item;
                });
    }

    private GalleryItemDto toGalleryItem(Upload upload, boolean isAdmin) {
        GalleryItemDto item = new GalleryItemDto();
        User uploader = upload.getUploadedBy();

        item.setId(upload.getUuid().toString());
        item.setTitle(upload.getUploadDescription() != null ? upload.getUploadDescription() : "Untitled");
        item.setFeatured(upload.isFeatured());
        item.setType(upload.getContentType().toString().toLowerCase());

        try {
            String key = r2StorageService.extractObjectKey(upload.getFileUrl());
            item.setSrc(r2StorageService.getSecureUrl(key, upload.isApproved(), isAdmin));
        } catch (SecurityException e) {
            log.warn("Access denied for upload {}: {}", upload.getUuid(), e.getMessage());
            item.setSrc(null);
        } catch (Exception e) {
            log.error("Failed to generate URL for upload {}: {}", upload.getUuid(), e.getMessage());
            item.setSrc(null);
        }

        item.setThumbnail(resolveSecureThumbnail(upload, isAdmin));

        if (upload.isAnon()) {
            item.setAuthor("Anonymous");
        } else if (uploader != null) {
            item.setAuthor(uploader.getFirstName() + " " + uploader.getLastName());
        } else {
            item.setAuthor("Unknown");
        }

        item.setEvent(upload.getMediaEvent().getName());

        item.setDate(upload.getCreatedDate() != null
                ? DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(upload.getCreatedDate())
                : "Unknown");

        return item;
    }

    private String resolveSecureThumbnail(Upload upload, boolean isAdmin) {
        if (upload.getFileUrl() == null) return null;
        try {
            String thumbKey = upload.getThumbnailUrl();
            if (thumbKey != null && !thumbKey.isBlank()) {
                return r2StorageService.getSecureThumbnailUrl(thumbKey, upload.isApproved(), isAdmin);
            }
            String key = r2StorageService.extractObjectKey(upload.getFileUrl());
            return r2StorageService.getSecureUrl(key, upload.isApproved(), isAdmin);
        } catch (SecurityException e) {
            return null;
        } catch (Exception e) {
            log.error("Failed to generate thumbnail URL for upload {}: {}", upload.getUuid(), e.getMessage());
            return null;
        }
    }
}
