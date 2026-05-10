package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.request.CreateEventDto;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.model.upload.EventStatus;
import com.ibrasoft.lensbridge.repository.upload.EventsRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventsService {

    private final EventsRepository eventsRepository;

    public MediaEvent createEvent(CreateEventDto event) {
        MediaEvent newMediaEvent = MediaEvent.builder()
                .name(event.getName())
                .date(event.getDate())
                .build();
        
        return eventsRepository.save(newMediaEvent);
    }

    public List<MediaEvent> getAllEvents() {
        return eventsRepository.findAll();
    }

    public MediaEvent createEvent(String eventName, Instant eventDate) {
        return createEvent(CreateEventDto.builder().name(eventName).date(eventDate).build());
    }

    public Optional<MediaEvent> getEventById(UUID id) {
        return eventsRepository.findById(id);
    }

    public MediaEvent updateEvent(MediaEvent mediaEvent) {
        return eventsRepository.save(mediaEvent);
    }

    public void deleteEvent(UUID id) {
        eventsRepository.deleteById(id);
    }

    @Scheduled(cron = "0 0 0 * * ?") // Runs daily at midnight
    public void cleanUpOldEvents() {
        this.cleanUpOldEvents(Instant.now());
    }

    public boolean isEventAcceptingUploads(UUID eventId) {
        Optional<MediaEvent> eventOpt = eventsRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return false;
        }
        MediaEvent mediaEvent = eventOpt.get();

        return mediaEvent.getStatus() == EventStatus.ONGOING ||
                (mediaEvent.getStatus() == EventStatus.PAST &&
                        mediaEvent.getDate().isAfter(Instant.now().minus(java.time.Duration.ofDays(7))));

    }

    public void cleanUpOldEvents(Instant now) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.ofInstant(now, zone);
        List<MediaEvent> allMediaEvents = eventsRepository.findAll();

        for (MediaEvent mediaEvent : allMediaEvents) {
            if (mediaEvent.getDate() == null)
                continue;

            LocalDate eventDate = LocalDate.ofInstant(mediaEvent.getDate(), zone);
            EventStatus newStatus;

            if (eventDate.isBefore(today)) {
                newStatus = EventStatus.PAST;
            } else if (eventDate.isAfter(today)) {
                newStatus = EventStatus.UPCOMING;
            } else {
                if (!now.isBefore(mediaEvent.getDate())) {
                    newStatus = EventStatus.ONGOING;
                } else {
                    newStatus = EventStatus.UPCOMING;
                }
            }

            mediaEvent.setStatus(newStatus);
            eventsRepository.save(mediaEvent);
        }
    }

}
