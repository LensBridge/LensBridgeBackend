package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTests {

    @Mock
    private EventsRepository eventsRepository;

    @InjectMocks
    private EventsService eventService;

    @Test
    void testPastEventGetsMarkedAsPast() {
        Event pastEvent = new Event();
        pastEvent.setDate(LocalDateTime.of(2023, 12, 1, 10, 0));

        when(eventsRepository.findAll()).thenReturn(List.of(pastEvent));

        eventService.cleanUpOldEvents(LocalDateTime.of(2025, 7, 28, 12, 0));

        assertEquals(EventStatus.PAST, pastEvent.getStatus());
        verify(eventsRepository).save(pastEvent);
    }

    @Test
    void testUpcomingEventGetsMarkedAsUpcoming() {
        Event futureEvent = new Event();
        futureEvent.setDate(LocalDateTime.of(2025, 12, 25, 10, 0));

        when(eventsRepository.findAll()).thenReturn(List.of(futureEvent));

        eventService.cleanUpOldEvents(LocalDateTime.of(2025, 7, 28, 12, 0));

        assertEquals(EventStatus.UPCOMING, futureEvent.getStatus());
        verify(eventsRepository).save(futureEvent);
    }

    @Test
    void testOngoingEventGetsMarkedAsOngoing() {
        Event ongoingEvent = new Event();
        ongoingEvent.setDate(LocalDateTime.of(2025, 7, 28, 8, 0));

        when(eventsRepository.findAll()).thenReturn(List.of(ongoingEvent));

        eventService.cleanUpOldEvents(LocalDateTime.of(2025, 7, 28, 12, 0));

        assertEquals(EventStatus.ONGOING, ongoingEvent.getStatus());
        verify(eventsRepository).save(ongoingEvent);
    }

    @Test
    void testEventTodayButHasNotStartedIsUpcoming() {
        Event upcomingToday = new Event();
        upcomingToday.setDate(LocalDateTime.of(2025, 7, 28, 18, 0)); // later today

        when(eventsRepository.findAll()).thenReturn(List.of(upcomingToday));

        eventService.cleanUpOldEvents(LocalDateTime.of(2025, 7, 28, 10, 0));

        assertEquals(EventStatus.UPCOMING, upcomingToday.getStatus());
        verify(eventsRepository).save(upcomingToday);
    }

    @Test
    void testNullDateIsIgnored() {
        Event event = new Event();
        event.setDate(null);

        when(eventsRepository.findAll()).thenReturn(List.of(event));

        eventService.cleanUpOldEvents(LocalDateTime.of(2025, 7, 28, 10, 0));

        verify(eventsRepository, never()).save(any());
    }

    @Test
    void testEventTodayGetsMarkedAsOngoingAndStaysUntilNextDay() {
        Event todayEvent = new Event();
        todayEvent.setDate(LocalDateTime.of(2025, 7, 28, 10, 0)); // today at 10 AM

        when(eventsRepository.findAll()).thenReturn(List.of(todayEvent));

        // First cleanup during the same day (e.g., 12 PM)
        eventService.cleanUpOldEvents(LocalDateTime.of(2025, 7, 28, 12, 0));
        assertEquals(EventStatus.ONGOING, todayEvent.getStatus());

        // Then cleanup the next day at 12:01 AM
        eventService.cleanUpOldEvents(LocalDateTime.of(2025, 7, 29, 0, 1));
        assertEquals(EventStatus.PAST, todayEvent.getStatus());

        // Make sure save was called twice
        verify(eventsRepository, times(2)).save(todayEvent);
    }

}
