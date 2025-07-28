package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadService uploadService;

    @MockitoBean
    private EventsService eventsService;

    @MockitoBean
    private AdminAuditService adminAuditService;

    @MockitoBean
    private UserService userService;


    private String adminBaseURL = "/api/admin";

    @Test
    void accessDeniedForUnauthenticatedUsers() throws Exception {
        mockMvc.perform(get(adminBaseURL + "/create-event"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {Role.ADMIN})
    void accessDeniedForUserRole() throws Exception {
        mockMvc.perform(get(adminBaseURL + "/create-event"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@mail.utoronto.ca",
            authorities = {"ROLE_ADMIN"})
    void accessGrantedForAdminRole() throws Exception {
        mockMvc.perform(post(adminBaseURL + "/create-event")
                        .param("eventName", "Test Event")
                        .param("eventDate", "2024-12-01T10:00:00")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());
    }
}
