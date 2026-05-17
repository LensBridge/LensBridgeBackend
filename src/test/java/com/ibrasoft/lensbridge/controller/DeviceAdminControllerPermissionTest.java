package com.ibrasoft.lensbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only integration tests for {@link DeviceAdminController}. Every endpoint is class-level
 * {@code @PreAuthorize("hasRole('ROOT')")}; this verifies anonymous -> 401, USER/ADMIN -> 403,
 * and ROOT -> not 401/403. CSRF is disabled, so no csrf() post-processor is needed.
 */
@WebMvcTest(controllers = DeviceAdminController.class)
@Import(MethodSecurityTestConfig.class)
class DeviceAdminControllerPermissionTest {

    private static final String BASE = "/api/admin/board/devices";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnrollmentTokenService enrollmentTokenService;
    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private DeviceCommandRepository commandRepository;
    @MockitoBean
    private CommandDispatcher commandDispatcher;
    @MockitoBean
    private ObjectMapper objectMapper;
    @MockitoBean
    private AgentSessionRegistry sessionRegistry;

    private static String id() {
        return UUID.randomUUID().toString();
    }

    @Test
    @WithAnonymousUser
    void anonymousIsUnauthorizedForAllEndpoints() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + id())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/enrollment-tokens")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + id() + "/revoke")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + id() + "/commands")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + id() + "/commands")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"USER", "ADMIN"})
    @WithMockUser
    void nonRootRolesAreForbidden(String role) throws Exception {
        runAllEndpoints(role, true);
    }

    @Test
    @WithMockUser(roles = "ROOT")
    void rootIsAuthorized() throws Exception {
        runAllEndpoints("ROOT", false);
    }

    private void runAllEndpoints(String role, boolean expectForbidden) throws Exception {
        RequestPostProcessor user = SecurityMockMvcRequestPostProcessors.user("u").roles(role);

        check(expectForbidden, mockMvc.perform(get(BASE).with(user)));
        check(expectForbidden, mockMvc.perform(get(BASE + "/" + id()).with(user)));
        check(expectForbidden, mockMvc.perform(post(BASE + "/enrollment-tokens").with(user)
                .contentType(MediaType.APPLICATION_JSON).content("{}")));
        check(expectForbidden, mockMvc.perform(post(BASE + "/" + id() + "/revoke").with(user)));
        check(expectForbidden, mockMvc.perform(post(BASE + "/" + id() + "/commands").with(user)
                .contentType(MediaType.APPLICATION_JSON).content("{}")));
        check(expectForbidden, mockMvc.perform(get(BASE + "/" + id() + "/commands").with(user)));
    }

    private void check(boolean expectForbidden, ResultActions actions) throws Exception {
        PermissionAssertions.expectAuthorizationOutcome(actions, expectForbidden);
    }
}
