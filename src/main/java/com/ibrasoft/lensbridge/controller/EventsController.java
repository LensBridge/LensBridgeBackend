package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.event.*;
import com.ibrasoft.lensbridge.service.EventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDate;
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
        List<Event> events = eventsService.getAllEvents().stream().filter(event -> event.getStatus() == EventStatus.ONGOING ||
                (event.getStatus() == EventStatus.PAST && event.getDate().isAfter(ChronoLocalDate.from(LocalDateTime.now().minusDays(7))))).toList();

        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable UUID id) {
        Optional<Event> event = eventsService.getEventById(id);
        return event.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody Event event) {
        Event savedEvent = eventsService.createEvent(event);
        return ResponseEntity.ok(savedEvent);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Event> updateEvent(@PathVariable UUID id, @RequestBody Event event) {
        Optional<Event> existingEvent = eventsService.getEventById(id);
        if (existingEvent.isPresent()) {
            event.setId(id);
            Event updatedEvent = eventsService.updateEvent(event);
            return ResponseEntity.ok(updatedEvent);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        Optional<Event> event = eventsService.getEventById(id);
        if (event.isPresent()) {
            eventsService.deleteEvent(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
