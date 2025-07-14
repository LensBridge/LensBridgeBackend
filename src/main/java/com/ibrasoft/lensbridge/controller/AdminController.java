package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.ADMIN + "')")
public class AdminController {

    private final UploadService uploadService;
    private final EventsService eventsService;

    @PostMapping("/create-event")
    public ResponseEntity<?> createEvent(@RequestParam("eventName") String eventName, @RequestParam(value = "eventDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate, @RequestParam(value = "status", required = false) EventStatus status) {
        Event event = new Event();
        UUID generatedId = UUID.randomUUID();
        event.setId(generatedId);
        event.setName(eventName);
        event.setDate(eventDate != null ? eventDate : LocalDate.now());
        event.setStatus(status != null ? status : EventStatus.ONGOING);

        Event savedEvent = eventsService.createEvent(event);
        return ResponseEntity.ok("Event created successfully: " + savedEvent.getName() + " with ID: " + savedEvent.getId());
    }

    @GetMapping("/events")
    public ResponseEntity<?> getAllEvents() {
        return ResponseEntity.ok(eventsService.getAllEvents());
    }

    @GetMapping("/uploads")
    public ResponseEntity<?> getAllUploads() {
        return ResponseEntity.ok(uploadService.getAllUploads());
    }
}
