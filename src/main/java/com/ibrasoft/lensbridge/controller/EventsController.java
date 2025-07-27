package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.service.EventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventsController {

    private final EventsService eventsService;

    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() {
        eventsService.cleanUpOldEvents();
        List<Event> events = eventsService.getAllEvents().stream()
                .filter(event -> event.getStatus() == EventStatus.ONGOING ||
                        (event.getStatus() == EventStatus.PAST &&
                                event.getDate().isAfter(LocalDateTime.now().minusDays(7))))
                .toList();
        return ResponseEntity.ok(events);
    }


    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable UUID id) {
        Optional<Event> event = eventsService.getEventById(id);
        return event.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }
}
