package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
        Event event = Event.builder().id(UUID.randomUUID()).name(name).date(date).status(EventStatus.UPCOMING).build();
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
        this.cleanUpOldEvents(LocalDateTime.now());
    }

    public void cleanUpOldEvents(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<Event> allEvents = eventsRepository.findAll();

        for (Event event : allEvents) {
            if (event.getDate() == null) continue;

            LocalDate eventDate = event.getDate().toLocalDate();
            EventStatus newStatus;

            if (eventDate.isBefore(today)) {
                newStatus = EventStatus.PAST;
            } else if (eventDate.isAfter(today)) {
                newStatus = EventStatus.UPCOMING;
            } else {
                if (!now.isBefore(event.getDate())) {
                    newStatus = EventStatus.ONGOING;
                } else {
                    newStatus = EventStatus.UPCOMING;
                }
            }

            event.setStatus(newStatus);
            eventsRepository.save(event);
        }
    }


}
