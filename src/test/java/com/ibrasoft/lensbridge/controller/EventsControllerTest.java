package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.config.TestSecurityConfig;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.service.EventsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestSecurityConfig.class)
@WebMvcTest(EventsController.class)
class EventsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventsService eventsService;

    @Test
    void testGetAllEvents() throws Exception {
        Mockito.when(eventsService.getAllEvents()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetEventByIdFound() throws Exception {
        UUID id = UUID.randomUUID();
        Event event = new Event();
        event.setId(id);
        event.setStatus(EventStatus.ONGOING);
        event.setDate(java.time.LocalDate.now());
        Mockito.when(eventsService.getEventById(id)).thenReturn(Optional.of(event));
        mockMvc.perform(get("/api/events/" + id))
                .andExpect(status().isOk());
    }

    @Test
    void testGetEventByIdNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.when(eventsService.getEventById(id)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/events/" + id))
                .andExpect(status().isNotFound());
    }
}
