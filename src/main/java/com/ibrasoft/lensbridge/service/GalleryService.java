package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.GalleryItemDto;
import com.ibrasoft.lensbridge.dto.GalleryResponseDto;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import com.ibrasoft.lensbridge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GalleryService {

    private final UploadService uploadService;
    private final UserRepository uploaderRepository;
    private final EventsRepository eventsRepository;
    private final CloudinaryService cloudinaryService;

    public Page<GalleryItemDto> getAllApprovedGalleryItems(Pageable pageable) {
        Page<Upload> uploads = uploadService.getAllApprovedUploads(pageable);
        return uploads.map(this::convertToGalleryItem); // super clean!
    }


    public Page<GalleryItemDto> getAllGalleryItems(Pageable pageable) {
        Page<Upload> uploads = uploadService.getAllUploads(pageable);
        return uploads.map(this::convertToGalleryItem);
    }

    public GalleryResponseDto getGalleryItemsByEvent(UUID eventId) {
        List<Upload> uploads = uploadService.getUploadsByEventId(eventId);
        List<GalleryItemDto> galleryItems = uploads.stream()
                .map(this::convertToGalleryItem)
                .collect(Collectors.toList());
        
        return new GalleryResponseDto(galleryItems);
    }

    private GalleryItemDto convertToGalleryItem(Upload upload) {
        GalleryItemDto item = new GalleryItemDto();
        User uploader = uploaderRepository.findById(upload.getUploadedBy())
                .orElse(null);
        
        // Basic info
        item.setId(upload.getUuid().toString());
        item.setSrc(upload.getFileUrl());
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

        String thumbnail = generateThumbnail(upload.getFileUrl(), upload.getContentType().toString().toLowerCase());
        item.setThumbnail(thumbnail);
        String eventName = getEventName(upload.getEventId());
        item.setEvent(eventName);
        
        // Format date
        String date = formatDate(upload);
        item.setDate(date);
        
        return item;
    }

    private boolean isVideoExtension(String extension) {
        return extension.equals("mp4") || extension.equals("avi") || extension.equals("mov") || 
               extension.equals("wmv") || extension.equals("flv") || extension.equals("webm");
    }

    private String generateThumbnail(String fileUrl, String contentType) {
        if (fileUrl == null) return null;
        
        try {
            String publicId = cloudinaryService.extractPublicIdFromUrl(fileUrl);
            if (publicId == null) return fileUrl; // Return original if can't extract public ID
            
            if ("video".equals(contentType)) {
                return cloudinaryService.generateVideoThumbnail(publicId);
            } else {
                return cloudinaryService.generateImageThumbnail(publicId);
            }
        } catch (Exception e) {
            // If thumbnail generation fails, return original URL
            return fileUrl;
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
