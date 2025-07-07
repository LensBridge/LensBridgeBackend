package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventsServiceIntegrationTest {
    @Mock
    private EventsRepository eventsRepository;

    @InjectMocks
    private EventsService eventsService;

    @Test
    void testCreateEvent() {
        Event event = new Event();
        when(eventsRepository.save(any(Event.class))).thenReturn(event);
        Event created = eventsService.createEvent(event);
        assertNotNull(created);
        verify(eventsRepository).save(event);
    }

    @Test
    void testGetAllEvents() {
        when(eventsRepository.findAll()).thenReturn(Collections.emptyList());
        assertTrue(eventsService.getAllEvents().isEmpty());
        verify(eventsRepository).findAll();
    }

    @Test
    void testGetEventById() {
        UUID id = UUID.randomUUID();
        Event event = new Event();
        when(eventsRepository.findById(id)).thenReturn(Optional.of(event));
        Optional<Event> found = eventsService.getEventById(id);
        assertTrue(found.isPresent());
        verify(eventsRepository).findById(id);
    }

    @Test
    void testUpdateEvent() {
        Event event = new Event();
        when(eventsRepository.save(event)).thenReturn(event);
        Event updated = eventsService.updateEvent(event);
        assertNotNull(updated);
        verify(eventsRepository).save(event);
    }

    @Test
    void testDeleteEvent() {
        UUID id = UUID.randomUUID();
        doNothing().when(eventsRepository).deleteById(id);
        eventsService.deleteEvent(id);
        verify(eventsRepository).deleteById(id);
    }
}
