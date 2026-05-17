package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.security.WebSecurityConfig;
import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.security.services.UserDetailsServiceImpl;
import com.ibrasoft.lensbridge.service.EventsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that every endpoint on {@link EventsController} is genuinely public
 * (matched by the {@code /api/events/**} permitAll rule in {@link WebSecurityConfig}).
 * A response that is NOT 401 and NOT 403 when called anonymously is a PASS.
 */
@WebMvcTest(EventsController.class)
@Import(WebSecurityConfig.class)
@TestPropertySource(properties = {
        "frontend.baseurl=http://localhost:3000",
        "musallahboard.baseurl=http://localhost:4000"
})
class EventsControllerPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventsService eventsService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    void getAllEvents_isPublic() throws Exception {
        when(eventsService.getPublicVisibleEvents()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk());
    }

    @Test
    void getEventById_isPublic() throws Exception {
        when(eventsService.getEventById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/events/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
