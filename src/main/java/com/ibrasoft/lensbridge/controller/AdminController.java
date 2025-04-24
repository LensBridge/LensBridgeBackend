package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.Event;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    @Autowired
    private final UploadRepository uploadRepository;

    @PostMapping("/create-event")
    public ResponseEntity<?> createEvent(@RequestParam("eventName") String eventName) {
        Event event = new Event();
        event.setEventName(eventName);

        return ResponseEntity.ok("Event created successfully: " + eventName);
    }
}
