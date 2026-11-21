package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.request.CreateEventDto;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.model.upload.EventStatus;
import com.ibrasoft.lensbridge.repository.upload.EventsRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Events carry a persisted {@link EventStatus}, but the stored value is only a cache: it is
 * refreshed every few minutes, so anything that makes a decision (accepting uploads, public
 * visibility) derives the status from the event date and the current time instead. Trusting the
 * column made an evening event UPCOMING until midnight and PAST right after, never ONGOING.
 */
@Service
public class EventsService {

    private final EventsRepository eventsRepository;
    private final int daysCutoffForPastEvent;
    private final Clock clock;

    @Autowired
    public EventsService(EventsRepository eventsRepository,
                         @Value("${lensbridge.app.daysCutoffForPastEvent:7}") int daysCutoffForPastEvent) {
        this(eventsRepository, daysCutoffForPastEvent, Clock.systemDefaultZone());
    }

    EventsService(EventsRepository eventsRepository, int daysCutoffForPastEvent, Clock clock) {
        this.eventsRepository = eventsRepository;
        this.daysCutoffForPastEvent = daysCutoffForPastEvent;
        this.clock = clock;
    }

    public MediaEvent createEvent(CreateEventDto event) {
        MediaEvent newMediaEvent = MediaEvent.builder()
                .name(event.getName())
                .date(event.getDate())
                .status(computeStatus(event.getDate(), clock.instant()))
                .build();

        return eventsRepository.save(newMediaEvent);
    }

    private EventStatus computeStatus(Instant eventDate, Instant now) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate eventDay = LocalDate.ofInstant(eventDate, zone);

        if (eventDay.isBefore(today)) return EventStatus.PAST;
        if (eventDay.isAfter(today)) return EventStatus.UPCOMING;
        return now.isBefore(eventDate) ? EventStatus.UPCOMING : EventStatus.ONGOING;
    }

    /** ONGOING, or PAST for no longer than the configured cutoff. */
    private boolean isOpenForUploads(MediaEvent event, Instant now) {
        EventStatus status = computeStatus(event.getDate(), now);
        return status == EventStatus.ONGOING
                || (status == EventStatus.PAST
                        && event.getDate().isAfter(now.minus(Duration.ofDays(daysCutoffForPastEvent))));
    }

    public List<MediaEvent> getAllEvents() {
        return eventsRepository.findAll();
    }

    public List<MediaEvent> getPublicVisibleEvents() {
        Instant now = clock.instant();
        return eventsRepository.findAll().stream()
                .filter(e -> isOpenForUploads(e, now))
                // A copy carrying the live status, so the response never contradicts the filter
                // that selected it (the entity itself may be a few minutes stale).
                .map(e -> MediaEvent.builder()
                        .id(e.getId())
                        .name(e.getName())
                        .date(e.getDate())
                        .status(computeStatus(e.getDate(), now))
                        .build())
                .toList();
    }

    public MediaEvent createEvent(String eventName, Instant eventDate) {
        return createEvent(CreateEventDto.builder().name(eventName).date(eventDate).build());
    }

    public Optional<MediaEvent> getEventById(UUID id) {
        return eventsRepository.findById(id);
    }

    public boolean isEventAcceptingUploads(UUID eventId) {
        return eventsRepository.findById(eventId)
                .map(event -> isOpenForUploads(event, clock.instant()))
                .orElse(false);
    }

    @Scheduled(cron = "0 */5 * * * ?")
    void refreshEventStatuses() {
        refreshEventStatuses(clock.instant());
    }

    /** Brings the stored status of every event up to date, writing only the rows that changed. */
    public void refreshEventStatuses(Instant now) {
        for (MediaEvent mediaEvent : eventsRepository.findAll()) {
            EventStatus current = computeStatus(mediaEvent.getDate(), now);
            if (current != mediaEvent.getStatus()) {
                mediaEvent.setStatus(current);
                eventsRepository.save(mediaEvent);
            }
        }
    }

}
