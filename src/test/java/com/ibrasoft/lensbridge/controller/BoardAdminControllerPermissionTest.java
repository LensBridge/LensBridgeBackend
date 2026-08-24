package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.PromotableSocialMediaService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only tests for {@link BoardAdminController}. Endpoints are authorized by
 * permission rather than by role, so what matters here is that a role holding adjacent
 * permissions is still refused — a BOARD_EDITOR must not reach board config.
 * <p>
 * CSRF is disabled in production, so no csrf() post-processor is needed.
 */
@WebMvcTest(controllers = BoardAdminController.class)
@Import(MethodSecurityTestConfig.class)
class BoardAdminControllerPermissionTest {

    private static final String BASE = "/api/admin/board";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PosterService posterService;
    @MockitoBean
    private PromotableSocialMediaService socialMediaService;
    @MockitoBean
    private BoardService boardService;
    @MockitoBean
    private AdminAuditService auditService;
    @MockitoBean
    private BoardStreamHandler boardStreamHandler;
    @MockitoBean
    private UploadService uploadService;

    /**
     * Not used by this controller. WebConfig registers CurrentUserArgumentResolver, which
     * needs a UserService, and the slice does not load the service layer.
     */
    @MockitoBean
    private UserService userService;

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private void expectForbidden(MockHttpServletRequestBuilder request, Role role) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.as(role))).andExpect(status().isForbidden());
    }

    /**
     * Authorized means "not rejected by security" — the handler's own outcome is out of scope.
     * <p>
     * 429 is rejected explicitly. It is not an authorization verdict, so an allow-assertion that
     * only looked for 401/403 would pass on a throttled request that never reached the security
     * layer, hiding the fact that the test proved nothing.
     */
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

    // ==================== Anonymous ====================

    @Test
    @WithAnonymousUser
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE + "/posters")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/configs")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/refresh")).andExpect(status().isUnauthorized());
    }

    // ==================== Viewer: reads yes, writes no ====================

    @Test
    void viewerCanReadContentAndConfig() throws Exception {
        expectAllowed(get(BASE + "/posters"), Role.BOARD_VIEWER);
        expectAllowed(get(BASE + "/events"), Role.BOARD_VIEWER);
        expectAllowed(get(BASE + "/socials"), Role.BOARD_VIEWER);
        expectAllowed(get(BASE + "/weekly-content"), Role.BOARD_VIEWER);
        expectAllowed(get(BASE + "/configs"), Role.BOARD_VIEWER);
    }

    @Test
    void viewerCannotWriteAnything() throws Exception {
        expectForbidden(delete(BASE + "/posters/" + id()), Role.BOARD_VIEWER);
        expectForbidden(delete(BASE + "/events/" + id()), Role.BOARD_VIEWER);
        expectForbidden(delete(BASE + "/socials/" + id()), Role.BOARD_VIEWER);
        expectForbidden(put(BASE + "/weekly-content/2026/1")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), Role.BOARD_VIEWER);
        expectForbidden(patch(BASE + "/configs/" + id())
                .contentType(MediaType.APPLICATION_JSON).content("{}"), Role.BOARD_VIEWER);
        expectForbidden(post(BASE + "/refresh"), Role.BOARD_VIEWER);
    }

    // ==================== Editor: content yes, board config no ====================

    @Test
    void editorCanWriteContent() throws Exception {
        expectAllowed(delete(BASE + "/posters/" + id()), Role.BOARD_EDITOR);
        expectAllowed(delete(BASE + "/events/" + id()), Role.BOARD_EDITOR);
        expectAllowed(delete(BASE + "/socials/" + id()), Role.BOARD_EDITOR);
        expectAllowed(put(BASE + "/weekly-content/2026/1")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), Role.BOARD_EDITOR);
        expectAllowed(post(BASE + "/refresh"), Role.BOARD_EDITOR);
    }

    /** The separation this redesign exists for: editing copy is not administering hardware. */
    @Test
    void editorCannotTouchBoardConfig() throws Exception {
        expectForbidden(patch(BASE + "/configs/" + id())
                .contentType(MediaType.APPLICATION_JSON).content("{}"), Role.BOARD_EDITOR);
        expectForbidden(put(BASE + "/configs/" + id())
                .contentType(MediaType.APPLICATION_JSON).content("{}"), Role.BOARD_EDITOR);
    }

    /** But the ticker is copy, so it has its own door. */
    @Test
    void editorCanWriteTheTickerWithoutConfigWrite() throws Exception {
        expectAllowed(patch(BASE + "/configs/" + id() + "/ticker")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), Role.BOARD_EDITOR);
    }

    // ==================== Non-board roles have no board access ====================

    @Test
    void plainUserHasNoBoardAccess() throws Exception {
        expectForbidden(get(BASE + "/posters"), Role.USER);
        expectForbidden(get(BASE + "/configs"), Role.USER);
    }

    // ==================== Root ====================

    @Test
    void rootIsAuthorizedEverywhere() throws Exception {
        expectAllowed(get(BASE + "/posters"), Role.ROOT);
        expectAllowed(get(BASE + "/configs"), Role.ROOT);
        expectAllowed(patch(BASE + "/configs/" + id())
                .contentType(MediaType.APPLICATION_JSON).content("{}"), Role.ROOT);
        expectAllowed(delete(BASE + "/posters/" + id()), Role.ROOT);
        expectAllowed(delete(BASE + "/socials/" + id()), Role.ROOT);
        expectAllowed(post(BASE + "/refresh"), Role.ROOT);
    }
}
