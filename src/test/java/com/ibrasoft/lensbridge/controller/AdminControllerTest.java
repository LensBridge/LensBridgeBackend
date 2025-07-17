package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.config.TestSecurityConfig;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.UploadService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(AdminController.class)
class AdminControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventsService eventsService;
    @MockBean
    private UploadService uploadService;

    @Test
    void testGetAllEvents() throws Exception {
        Mockito.when(eventsService.getAllEvents()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/admin/events"))
                .andExpect(status().isOk());
    }

//    @Test
//    void testGetAllUploads() throws Exception {
//        Mockito.when(uploadService.getAllUploads()).thenReturn(Collections.emptyList());
//        mockMvc.perform(get("/api/admin/uploads"))
//                .andExpect(status().isOk());
//    }

    @Test
    void testCreateEvent() throws Exception {
        Event event = new Event();
        event.setId(java.util.UUID.randomUUID());
        event.setName("Test Event");
        event.setDate(LocalDate.now());
        event.setStatus(EventStatus.ONGOING);
        Mockito.when(eventsService.createEvent(Mockito.any(Event.class))).thenReturn(event);
        mockMvc.perform(post("/api/admin/create-event")
                .param("eventName", "Test Event")
                .param("eventDate", LocalDate.now().toString())
                .param("status", EventStatus.ONGOING.name()))
                .andExpect(status().isOk());
    }
}
