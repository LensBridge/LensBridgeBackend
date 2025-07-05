package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventsService {

    private final EventsRepository eventsRepository;

    public Event createEvent(Event event) {
        if (event.getId() == null) {
            event.setId(UUID.randomUUID());
        }
        return eventsRepository.save(event);
    }

    public List<Event> getAllEvents() {
        return eventsRepository.findAll();
    }

    public Optional<Event> getEventById(UUID id) {
        return eventsRepository.findById(id);
    }

    public Event updateEvent(Event event) {
        return eventsRepository.save(event);
    }

    public void deleteEvent(UUID id) {
        eventsRepository.deleteById(id);
    }
}
