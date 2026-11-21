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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

    /**
     * The public gallery is unauthenticated, so the client-supplied paging is bounded: any
     * other sort property would let a caller order by (and so probe) columns such as the
     * uploader's email, and an unknown one would surface as a 500.
     */
    static final Set<String> SORTABLE_PROPERTIES = Set.of("createdDate", "featured");
    static final int MAX_PAGE_SIZE = 100;

    private final UploadRepository uploadRepository;
    private final UserService userService;
    private final EventsRepository eventsRepository;
    private final R2StorageService r2StorageService;

    public Page<GalleryItemDto> getAllApprovedGalleryItems(Pageable pageable) {
        return uploadRepository.findByApprovedTrueAndDeletedAtIsNull(sanitize(pageable))
                .map(u -> toGalleryItem(u, false));
    }

    public Page<GalleryItemDto> getGalleryItemsByEvent(UUID eventId, Pageable pageable) {
        MediaEvent mediaEvent = eventsRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        return uploadRepository.findByMediaEventAndApprovedTrueAndDeletedAtIsNull(mediaEvent, sanitize(pageable))
                .map(u -> toGalleryItem(u, false));
    }

    public Page<GalleryItemDto> getUserGallery(UUID userId, Pageable pageable) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return uploadRepository.findByUploadedByAndDeletedAtIsNull(user, pageable)
                .map(upload -> {
                    GalleryItemDto item = toGalleryItem(upload, true);
                    // always show real name for own uploads regardless of anon flag
                    item.setAuthor(user.getFirstName() + " " + user.getLastName());
                    return item;
                });
    }

    /** @throws IllegalArgumentException (a 400) for a sort property outside the whitelist */
    private Pageable sanitize(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new IllegalArgumentException("Cannot sort by '" + order.getProperty()
                        + "'; sortable properties are " + new TreeSet<>(SORTABLE_PROPERTIES));
            }
        }
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, MAX_PAGE_SIZE, pageable.getSort());
        }
        return PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), pageable.getSort());
    }

    private GalleryItemDto toGalleryItem(Upload upload, boolean isAdmin) {
        GalleryItemDto item = new GalleryItemDto();
        User uploader = upload.getUploadedBy();

        item.setId(upload.getUuid().toString());
        item.setTitle(upload.getUploadDescription() != null ? upload.getUploadDescription() : "Untitled");
        item.setFeatured(upload.isFeatured());
        item.setType(upload.getContentType().toString().toLowerCase());

        try {
            item.setSrc(r2StorageService.getSecureUrlForStoredFile(upload.getFileUrl(), upload.isApproved(), isAdmin));
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

        // The event column is nullable, so an upload can outlive its event link.
        item.setEvent(upload.getMediaEvent() != null ? upload.getMediaEvent().getName() : "Unknown");

        item.setDate(upload.getCreatedDate() != null
                ? DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(upload.getCreatedDate())
                : "Unknown");

        return item;
    }

    private String resolveSecureThumbnail(Upload upload, boolean isAdmin) {
        if (upload.getFileUrl() == null) return null;
        try {
            return r2StorageService.getSecureThumbnailUrlOrOriginal(
                    upload.getFileUrl(), upload.getThumbnailUrl(), upload.isApproved(), isAdmin);
        } catch (SecurityException e) {
            return null;
        } catch (Exception e) {
            log.error("Failed to generate thumbnail URL for upload {}: {}", upload.getUuid(), e.getMessage());
            return null;
        }
    }
}
