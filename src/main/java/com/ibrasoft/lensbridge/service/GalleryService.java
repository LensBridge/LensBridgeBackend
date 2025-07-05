package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.GalleryItemDto;
import com.ibrasoft.lensbridge.dto.GalleryResponseDto;
import com.ibrasoft.lensbridge.model.Upload;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GalleryService {

    private final UploadService uploadService;
    private final EventsRepository eventsRepository;
    private final CloudinaryService cloudinaryService;

    public GalleryResponseDto getAllGalleryItems() {
        List<Upload> uploads = uploadService.getAllUploads();
        List<GalleryItemDto> galleryItems = uploads.stream().map(this::convertToGalleryItem).collect(Collectors.toList());

        return new GalleryResponseDto(galleryItems);
    }

    public GalleryResponseDto getGalleryItemsByEvent(UUID eventId) {
        List<Upload> uploads = uploadService.getUploadsByEventId(eventId);
        List<GalleryItemDto> galleryItems = uploads.stream().map(this::convertToGalleryItem).collect(Collectors.toList());

        return new GalleryResponseDto(galleryItems);
    }

    private GalleryItemDto convertToGalleryItem(Upload upload) {
        GalleryItemDto item = new GalleryItemDto();

        // Basic info
        item.setId(upload.getUuid().toString());
        item.setSrc(upload.getFileUrl());
        item.setTitle(upload.getUploadDescription() != null ? upload.getUploadDescription() : "Untitled");
        item.setFeatured(upload.isFeatured());
        item.setLikes(upload.getLikes());
        item.setViews(upload.getViews());

        // Determine content type
        String contentType = determineContentType(upload);
        item.setType(contentType);

        // Generate thumbnail
        String thumbnail = generateThumbnail(upload.getFileUrl(), contentType);
        item.setThumbnail(thumbnail);

        // Get author info
        String author = upload.getInstagramHandle() != null ? upload.getInstagramHandle() : "Anonymous";
        item.setAuthor(author);

        // Get event info
        String eventName = getEventName(upload.getEventId());
        item.setEvent(eventName);

        // Format date
        String date = formatDate(upload);
        item.setDate(date);

        return item;
    }

    private String determineContentType(Upload upload) {
        if (upload.getContentType() != null) {
            return upload.getContentType().startsWith("video") ? "video" : "image";
        }

        // Fallback: determine by file extension
        String fileName = upload.getFileName();
        if (fileName != null) {
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            return isVideoExtension(extension) ? "video" : "image";
        }

        return "image"; // Default
    }

    private boolean isVideoExtension(String extension) {
        return extension.equals("mp4") || extension.equals("avi") || extension.equals("mov") || extension.equals("wmv") || extension.equals("flv") || extension.equals("webm");
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

        return eventsRepository.findEventById(eventId).map(Event::getName).orElse("General");
    }

    private String formatDate(Upload upload) {
        if (upload.getCreatedDate() != null) {
            return upload.getCreatedDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return "Unknown";
    }
}
