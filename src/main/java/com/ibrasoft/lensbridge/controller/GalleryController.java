package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.upload.response.GalleryItemDto;
import com.ibrasoft.lensbridge.service.GalleryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GalleryController {

    private final GalleryService galleryService;

    @GetMapping("/gallery")
    public ResponseEntity<Page<GalleryItemDto>> getAllUploads(Pageable pageable) {
        return ResponseEntity.ok(galleryService.getAllApprovedGalleryItems(pageable));
    }

    @GetMapping("/gallery/event/{eventId}")
    public ResponseEntity<Page<GalleryItemDto>> getGalleryByEvent(@PathVariable UUID eventId, Pageable pageable) {
        return ResponseEntity.ok(galleryService.getGalleryItemsByEvent(eventId, pageable));
    }
}
