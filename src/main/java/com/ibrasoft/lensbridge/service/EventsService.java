package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.request.CreateEventDto;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.model.upload.EventStatus;
import com.ibrasoft.lensbridge.repository.upload.EventsRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${lensbridge.app.daysCutoffForPastEvent:7}")
    private int daysCutoffForPastEvent;

    public MediaEvent createEvent(CreateEventDto event) {
        MediaEvent newMediaEvent = MediaEvent.builder()
                .name(event.getName())
                .date(event.getDate())
                .status(computeStatus(event.getDate(), Instant.now()))
                .build();

        return eventsRepository.save(newMediaEvent);
    }

    private EventStatus computeStatus(Instant eventDate, Instant now) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate eventDay = LocalDate.ofInstant(eventDate, zone);

        if (eventDay.isBefore(today)) return EventStatus.PAST;
        if (eventDay.isAfter(today)) return EventStatus.UPCOMING;
        return now.isBefore(eventDate) ? EventStatus.UPCOMING : EventStatus.ONGOING;
    }

    public List<MediaEvent> getAllEvents() {
        return eventsRepository.findAll();
    }

    public List<MediaEvent> getPublicVisibleEvents() {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(daysCutoffForPastEvent));
        return eventsRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.ONGOING ||
                        (e.getStatus() == EventStatus.PAST && e.getDate().isAfter(cutoff)))
                .toList();
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

    @Scheduled(cron = "0 0 0 * * ?")
    void cleanUpOldEvents() {
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
        List<MediaEvent> allMediaEvents = eventsRepository.findAll();

        for (MediaEvent mediaEvent : allMediaEvents) {
            if (mediaEvent.getDate() == null)
                continue;

            mediaEvent.setStatus(computeStatus(mediaEvent.getDate(), now));
            eventsRepository.save(mediaEvent);
        }
    }

}
