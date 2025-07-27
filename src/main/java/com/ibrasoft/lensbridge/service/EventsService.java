package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
        Event saved = eventsRepository.save(event);
        this.cleanUpOldEvents();
        return saved;
    }

    public Event createEvent(String name, LocalDateTime date) {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .name(name)
                .date(date)
                .status(EventStatus.UPCOMING)
                .build();
        return createEvent(event);
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

    @Scheduled(cron = "0 0 0 * * ?") // Runs daily at midnight
    public void cleanUpOldEvents() {
        List<Event> allEvents = eventsRepository.findAll();
        for (Event event : allEvents) {
            // If the event is date has passed, update the status to PAST
            if (event.getDate() != null && event.getDate().isBefore(java.time.LocalDateTime.now())) {
                event.setStatus(EventStatus.PAST);
                eventsRepository.save(event);
            }
            // If the event is today, update the status to ONGOING
            else if (event.getDate() != null && event.getDate().isEqual(java.time.LocalDateTime.now())) {
                event.setStatus(EventStatus.ONGOING);
                eventsRepository.save(event);
            }
            // If the event is in the future, update the status to UPCOMING
            else if (event.getDate() != null && event.getDate().isAfter(java.time.LocalDateTime.now())) {
                event.setStatus(EventStatus.UPCOMING);
                eventsRepository.save(event);
            }
        }
    }
}
