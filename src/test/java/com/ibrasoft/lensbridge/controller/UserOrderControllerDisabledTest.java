package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ibrasoft.lensbridge.model.auth.Role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the tCketManage user endpoints do not exist when
 * {@code tcketmanage.enabled} is not set. The {@code @ConditionalOnProperty} on
 * {@link UserOrderController} prevents it from registering as a bean, so every
 * request under {@code /api/user/orders} and {@code /api/user/tickets} should
 * 404 rather than 401/403.
 */
@WebMvcTest(controllers = UserOrderController.class)
@Import(MethodSecurityTestConfig.class)
class UserOrderControllerDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void ordersEndpointNotMapped() throws Exception {
        mockMvc.perform(get("/api/user/orders").with(TestAuthorities.as(Role.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderByIdNotMapped() throws Exception {
        mockMvc.perform(get("/api/user/orders/00000000-0000-0000-0000-000000000001")
                .with(TestAuthorities.as(Role.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrderNotMapped() throws Exception {
        mockMvc.perform(post("/api/user/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(TestAuthorities.as(Role.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrderNotMapped() throws Exception {
        mockMvc.perform(post("/api/user/orders/00000000-0000-0000-0000-000000000001/cancel")
                .with(TestAuthorities.as(Role.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ticketsEndpointNotMapped() throws Exception {
        mockMvc.perform(get("/api/user/tickets").with(TestAuthorities.as(Role.USER)))
                .andExpect(status().isNotFound());
    }
}
