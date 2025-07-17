package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.GalleryItemDto;
import com.ibrasoft.lensbridge.dto.GalleryResponseDto;
import com.ibrasoft.lensbridge.service.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    @GetMapping("/gallery")
    public ResponseEntity<Page<GalleryItemDto>> getAllUploads(Pageable pageable) {
        try {
            Page<GalleryItemDto> response = galleryService.getAllApprovedGalleryItems(pageable);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching gallery: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Page.empty());
        }
    }

    @GetMapping("/gallery/event/{eventId}")
    public ResponseEntity<GalleryResponseDto> getGalleryByEvent(@PathVariable UUID eventId) {
        try {
            GalleryResponseDto response = galleryService.getGalleryItemsByEvent(eventId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Log the error for debugging
            System.err.println("Error fetching gallery for event: " + e.getMessage());
            e.printStackTrace();
            
            // Return empty response instead of error for better UX
            return ResponseEntity.ok(new GalleryResponseDto());
        }
    }
}
