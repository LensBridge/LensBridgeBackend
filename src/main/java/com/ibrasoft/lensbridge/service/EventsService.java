package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.request.CreateEventDto;
import com.ibrasoft.lensbridge.model.upload.Event;
import com.ibrasoft.lensbridge.model.upload.EventStatus;
import com.ibrasoft.lensbridge.repository.upload.EventsRepository;

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

    public Event createEvent(CreateEventDto event) {
        Event newEvent = Event.builder()
                .name(event.getName())
                .date(event.getDate())
                .build();
        
        return eventsRepository.save(newEvent);
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

    public boolean isEventAcceptingUploads(UUID eventId) {
        Optional<Event> eventOpt = eventsRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return false;
        }
        Event event = eventOpt.get();

        return event.getStatus() == EventStatus.ONGOING ||
                (event.getStatus() == EventStatus.PAST &&
                        event.getDate().isAfter(LocalDateTime.now().minusDays(7)));

    }

    public void cleanUpOldEvents(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<Event> allEvents = eventsRepository.findAll();

        for (Event event : allEvents) {
            if (event.getDate() == null)
                continue;

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
