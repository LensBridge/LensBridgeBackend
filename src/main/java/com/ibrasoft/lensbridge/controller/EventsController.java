package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.service.EventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventsController {

    private final EventsService eventsService;

    @GetMapping
    public ResponseEntity<List<MediaEvent>> getAllEvents() {
        return ResponseEntity.ok(eventsService.getPublicVisibleEvents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaEvent> getEventById(@PathVariable UUID id) {
        return eventsService.getEventById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
