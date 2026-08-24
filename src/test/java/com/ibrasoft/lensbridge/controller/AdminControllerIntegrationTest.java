package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminController} now carries only IAM and audit endpoints, each individually
 * gated by permission. There is no class-level authorization annotation.
 */
@WebMvcTest(controllers = AdminController.class)
@Import(MethodSecurityTestConfig.class)
class AdminControllerIntegrationTest {

    private static final String BASE = "/api/admin";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuditService adminAuditService;
    @MockitoBean
    private UserService userService;

    private void expectForbidden(MockHttpServletRequestBuilder request, Role role) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.as(role))).andExpect(status().isForbidden());
    }

    private void expectAllowed(MockHttpServletRequestBuilder request, Role role) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.as(role))).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 429) {
                throw new AssertionError("Request was rate limited (HTTP 429), so authorization for "
                        + role + " was never exercised");
            }
            if (s == 401 || s == 403) {
                throw new AssertionError("Expected " + role + " to be authorized but got HTTP " + s);
            }
        });
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE + "/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/audit")).andExpect(status().isUnauthorized());
    }

    // ==================== Identity: permission-gated ====================

    @Test
    void listingUsersRequiresIamUserRead() throws Exception {
        mockMvc.perform(get(BASE + "/users").with(
                        TestAuthorities.asHolderOf(Permission.IAM_USER_READ)))
                .andExpect(status().isOk());
    }

    @Test
    void boardRolesCannotListUsersOrGrantRoles() throws Exception {
        expectForbidden(get(BASE + "/users"), Role.BOARD_ADMIN);
        expectForbidden(get(BASE + "/roles"), Role.BOARD_ADMIN);
    }

    @Test
    void onlyRootCanGrantRoles() throws Exception {
        expectAllowed(get(BASE + "/roles"), Role.ROOT);
        expectAllowed(get(BASE + "/permissions"), Role.ROOT);
        expectForbidden(get(BASE + "/users"), Role.BOARD_EDITOR);
    }

    // ==================== Audit: permission-gated ====================

    @Test
    void auditIsReadableByAnyoneHoldingAuditRead() throws Exception {
        expectAllowed(get(BASE + "/audit"), Role.BOARD_ADMIN);
        expectAllowed(get(BASE + "/audit/actions"), Role.BOARD_ADMIN);
    }

    @Test
    void auditIsNotReadableWithoutAuditRead() throws Exception {
        expectForbidden(get(BASE + "/audit"), Role.BOARD_EDITOR);
        expectForbidden(get(BASE + "/audit"), Role.USER);
    }
}
