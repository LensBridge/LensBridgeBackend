package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminController} carries a mixed authorization model, deliberately: media
 * moderation stays on the legacy {@code hasRole('ADMIN')} class-level annotation, while the
 * identity and audit endpoints moved to permissions. This pins both halves — in particular
 * that the board roles gained no media access on the way through.
 */
@WebMvcTest(controllers = AdminController.class)
@Import(MethodSecurityTestConfig.class)
class AdminControllerIntegrationTest {

    private static final String BASE = "/api/admin";

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

    private void expectForbidden(MockHttpServletRequestBuilder request, Role role) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.as(role))).andExpect(status().isForbidden());
    }

    private void expectAllowed(MockHttpServletRequestBuilder request, Role role) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.as(role))).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 401 || s == 403) {
                throw new AssertionError("Expected " + role + " to be authorized but got HTTP " + s);
            }
        });
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE + "/uploads")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/users")).andExpect(status().isUnauthorized());
    }

    // ==================== Legacy media: still role-gated, unchanged ====================

    @Test
    void mediaModerationStillRequiresTheAdminRole() throws Exception {
        expectAllowed(get(BASE + "/uploads"), Role.ADMIN);
        expectAllowed(get(BASE + "/uploads/pending"), Role.ADMIN);
        expectForbidden(get(BASE + "/uploads"), Role.USER);
    }

    /**
     * ROOT is defined as holding every permission, so it must not be refused the one place
     * still gated by a role name. Before the class-level gate also accepted the permission,
     * a ROOT-only account got 403 here and needed ROLE_ADMIN granted separately.
     */
    @Test
    void rootReachesMediaWithoutAlsoHoldingTheAdminRole() throws Exception {
        expectAllowed(get(BASE + "/uploads"), Role.ROOT);
        expectAllowed(get(BASE + "/uploads/pending"), Role.ROOT);
        expectAllowed(post(BASE + "/feature-upload/" + java.util.UUID.randomUUID()), Role.ROOT);
    }

    /** The permission alone opens the gate — no role authority present at all. */
    @Test
    void theModeratePermissionAloneIsSufficient() throws Exception {
        mockMvc.perform(get(BASE + "/uploads").with(
                        TestAuthorities.asHolderOf(Permission.MEDIA_UPLOAD_MODERATE)))
                .andExpect(status().isOk());
    }

    /** Widening the gate must not have widened it to anyone else. */
    @Test
    void noOtherPermissionOpensTheMediaGate() throws Exception {
        mockMvc.perform(get(BASE + "/uploads").with(
                        TestAuthorities.asHolderOf(Permission.MEDIA_UPLOAD_READ)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/uploads").with(
                        TestAuthorities.asHolderOf(Permission.BOARD_CONTENT_READ)))
                .andExpect(status().isForbidden());
    }

    /** The point of keeping media on roles: board roles must not have drifted into it. */
    @Test
    void boardRolesHaveNoMediaAccess() throws Exception {
        expectForbidden(get(BASE + "/uploads"), Role.BOARD_ADMIN);
        expectForbidden(get(BASE + "/uploads"), Role.BOARD_EDITOR);
        // A mutating media endpoint, not just a read. Chosen for taking no query params:
        // Spring resolves handler arguments before method security runs, so an endpoint with
        // required params answers 500 for a missing param before authorization is consulted.
        expectForbidden(post(BASE + "/feature-upload/" + java.util.UUID.randomUUID()),
                Role.BOARD_ADMIN);
    }

    // ==================== Identity: permission-gated ====================

    @Test
    void listingUsersRequiresIamUserRead() throws Exception {
        mockMvc.perform(get(BASE + "/users").with(
                        TestAuthorities.asHolderOf(Permission.IAM_USER_READ)))
                .andExpect(status().isOk());
    }

    /**
     * ADMIN moderates media; it does not read the user directory. Method-level
     * {@code @PreAuthorize} replaces the class-level annotation rather than combining with
     * it, so this is the whole check for these endpoints.
     */
    @Test
    void mediaAdminCannotListUsersOrGrantRoles() throws Exception {
        expectForbidden(get(BASE + "/users"), Role.ADMIN);
        expectForbidden(get(BASE + "/roles"), Role.ADMIN);
    }

    @Test
    void onlyRootCanGrantRoles() throws Exception {
        expectAllowed(get(BASE + "/roles"), Role.ROOT);
        expectAllowed(get(BASE + "/permissions"), Role.ROOT);
        expectForbidden(get(BASE + "/users"), Role.BOARD_ADMIN);
    }

    // ==================== Audit: permission-gated ====================

    @Test
    void auditIsReadableByAnyoneHoldingAuditRead() throws Exception {
        expectAllowed(get(BASE + "/audit"), Role.ADMIN);
        // BOARD_ADMIN needs the audit log for device actions without gaining media moderation.
        expectAllowed(get(BASE + "/audit"), Role.BOARD_ADMIN);
        expectAllowed(get(BASE + "/audit/actions"), Role.BOARD_ADMIN);
    }

    @Test
    void auditIsNotReadableWithoutAuditRead() throws Exception {
        expectForbidden(get(BASE + "/audit"), Role.BOARD_EDITOR);
        expectForbidden(get(BASE + "/audit"), Role.USER);
    }
}
