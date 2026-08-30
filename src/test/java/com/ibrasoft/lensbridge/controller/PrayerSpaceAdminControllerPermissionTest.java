package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.PrayerSpaceService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only tests for {@link PrayerSpaceAdminController}.
 * <p>
 * The split under test is reads from writes. Seeing where the musallahs are is not a
 * privilege — the same data is served unauthenticated under {@code /api/minbar} — so reads
 * sit on {@code board:content:read}, which every board role carries. Editing the walking
 * directions somebody follows through a building at Maghrib is a different matter, and sits
 * on its own {@code board:prayerspace:write}. A BOARD_VIEWER holding the read permission
 * must therefore be able to list and not to write, which is the case a class-level
 * annotation would have collapsed.
 * <p>
 * CSRF is disabled in production, so no csrf() post-processor is needed.
 */
@WebMvcTest(controllers = PrayerSpaceAdminController.class)
@Import(MethodSecurityTestConfig.class)
class PrayerSpaceAdminControllerPermissionTest {

    private static final String BASE = "/api/admin/minbar/prayer-spaces";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrayerSpaceService prayerSpaceService;
    @MockitoBean
    private AdminAuditService auditService;

    /**
     * Not used by this controller. WebConfig registers CurrentUserArgumentResolver, which
     * needs a UserService, and the slice does not load the service layer.
     */
    @MockitoBean
    private UserService userService;

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static MockHttpServletRequestBuilder createRequest() {
        return post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Brother's Musallah","tag":"Main brothers' space",
                         "building":"CCT","type":"brothers"}
                        """);
    }

    private static MockHttpServletRequestBuilder updateRequest() {
        return patch(BASE + "/" + id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"capacity\":25}");
    }

    private void expectForbidden(MockHttpServletRequestBuilder request, Permission... held) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.asHolderOf(held)))
                .andExpect(status().isForbidden());
    }

    /**
     * Authorized means "not rejected by security" — the handler's own outcome is out of scope,
     * and the service is a mock, so a 200 here proves nothing about behaviour and is not
     * meant to.
     */
    private void expectAllowed(MockHttpServletRequestBuilder request, Permission... held) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.asHolderOf(held))).andExpect(result -> {
            int status = result.getResponse().getStatus();
            if (status == 401 || status == 403) {
                throw new AssertionError("Expected the request to be authorized but got HTTP " + status);
            }
        });
    }

    private void expectAllowedAs(MockHttpServletRequestBuilder request, Role role) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.as(role))).andExpect(result -> {
            int status = result.getResponse().getStatus();
            if (status == 401 || status == 403) {
                throw new AssertionError("Expected " + role + " to be authorized but got HTTP " + status);
            }
        });
    }

    // ==================== Anonymous ====================

    @Test
    @WithAnonymousUser
    void anonymousCannotReachTheAdminList() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotCreateASpace() throws Exception {
        mockMvc.perform(createRequest()).andExpect(status().isUnauthorized());
    }

    // ==================== Reads ====================

    @Test
    void contentReadCanListEverySpace() throws Exception {
        expectAllowed(get(BASE), Permission.BOARD_CONTENT_READ);
    }

    @Test
    void contentReadCanFetchOneSpace() throws Exception {
        expectAllowed(get(BASE + "/" + id()), Permission.BOARD_CONTENT_READ);
    }

    /** The write permission is not a superset of the read one; they are granted separately. */
    @Test
    void prayerSpaceWriteAloneCannotList() throws Exception {
        expectForbidden(get(BASE), Permission.BOARD_PRAYER_SPACE_WRITE);
    }

    @Test
    void anAdjacentBoardPermissionDoesNotConferReads() throws Exception {
        expectForbidden(get(BASE), Permission.BOARD_POSTER_WRITE);
    }

    // ==================== Writes ====================

    @Test
    void prayerSpaceWriteCanCreate() throws Exception {
        expectAllowed(createRequest(), Permission.BOARD_PRAYER_SPACE_WRITE);
    }

    @Test
    void prayerSpaceWriteCanUpdate() throws Exception {
        expectAllowed(updateRequest(), Permission.BOARD_PRAYER_SPACE_WRITE);
    }

    @Test
    void prayerSpaceWriteCanDelete() throws Exception {
        expectAllowed(delete(BASE + "/" + id()), Permission.BOARD_PRAYER_SPACE_WRITE);
    }

    /**
     * The regression this file exists for. Reading the prayer spaces is deliberately cheap to
     * grant; rewriting somebody's route to the musallah is not.
     */
    @Test
    void contentReadAloneCannotCreate() throws Exception {
        expectForbidden(createRequest(), Permission.BOARD_CONTENT_READ);
    }

    @Test
    void contentReadAloneCannotUpdate() throws Exception {
        expectForbidden(updateRequest(), Permission.BOARD_CONTENT_READ);
    }

    @Test
    void contentReadAloneCannotDelete() throws Exception {
        expectForbidden(delete(BASE + "/" + id()), Permission.BOARD_CONTENT_READ);
    }

    /** Board content and prayer spaces are separate grants, in both directions. */
    @Test
    void aPosterEditorCannotRewriteDirections() throws Exception {
        expectForbidden(updateRequest(), Permission.BOARD_POSTER_WRITE, Permission.BOARD_CONTENT_READ);
    }

    // ==================== Role bundles ====================

    @Test
    void boardViewerCanReadButNotWrite() throws Exception {
        expectAllowedAs(get(BASE), Role.BOARD_VIEWER);
        mockMvc.perform(createRequest().with(TestAuthorities.as(Role.BOARD_VIEWER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void boardEditorCanWrite() throws Exception {
        expectAllowedAs(createRequest(), Role.BOARD_EDITOR);
        expectAllowedAs(updateRequest(), Role.BOARD_EDITOR);
        expectAllowedAs(delete(BASE + "/" + id()), Role.BOARD_EDITOR);
    }

    @Test
    void boardAdminCanWrite() throws Exception {
        expectAllowedAs(createRequest(), Role.BOARD_ADMIN);
    }

    @Test
    void rootCanWrite() throws Exception {
        expectAllowedAs(createRequest(), Role.ROOT);
    }

    /** Owning ticketing is not owning the map. */
    @Test
    void aTicketingAdminHasNoReachHere() throws Exception {
        mockMvc.perform(get(BASE).with(TestAuthorities.as(Role.TCKET_ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(createRequest().with(TestAuthorities.as(Role.TCKET_ADMIN)))
                .andExpect(status().isForbidden());
    }
}
