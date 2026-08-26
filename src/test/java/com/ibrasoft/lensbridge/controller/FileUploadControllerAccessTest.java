package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.service.UploadLimitsService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UploadWorkflowService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read endpoints on {@link FileUploadController} used to hand any signed-in user any upload by
 * UUID, and any event's full moderation queue. Both now scope the read to the caller: the caller's
 * own id, plus whether they hold {@code media:upload:read}. These tests pin the wiring that decides
 * those two arguments — the filtering itself lives in {@code UploadServiceVisibilityTest}.
 *
 * <p>CSRF is disabled in production, so no csrf() post-processor is needed.
 */
@WebMvcTest(controllers = FileUploadController.class)
@Import(MethodSecurityTestConfig.class)
class FileUploadControllerAccessTest {

    private static final String BASE = "/api/upload";
    private static final UUID CALLER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UPLOAD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EVENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadService uploadService;
    @MockitoBean
    private UploadWorkflowService uploadWorkflowService;
    @MockitoBean
    private UploadLimitsService uploadLimitsService;

    /** Backs the CurrentUserArgumentResolver that WebConfig registers. */
    @MockitoBean
    private UserService userService;

    @BeforeEach
    void stubCaller() {
        User caller = new User();
        caller.setId(CALLER_ID);
        caller.setEmail("u@example.com");
        when(userService.findByEmail(anyString())).thenReturn(Optional.of(caller));
    }

    @Test
    void singleUploadReadIsScopedToTheCallerForAnOrdinaryUser() throws Exception {
        when(uploadService.getUploadByIdAsDto(UPLOAD_ID, CALLER_ID, false))
                .thenReturn(Optional.of(new UploadDto()));

        mockMvc.perform(get(BASE + "/" + UPLOAD_ID).with(TestAuthorities.as(Role.USER)))
                .andExpect(status().isOk());

        verify(uploadService).getUploadByIdAsDto(UPLOAD_ID, CALLER_ID, false);
    }

    @Test
    void singleUploadReadIsUnrestrictedForAHolderOfMediaUploadRead() throws Exception {
        when(uploadService.getUploadByIdAsDto(UPLOAD_ID, CALLER_ID, true))
                .thenReturn(Optional.of(new UploadDto()));

        mockMvc.perform(get(BASE + "/" + UPLOAD_ID).with(TestAuthorities.as(Role.USER, Role.BOARD_ADMIN)))
                .andExpect(status().isOk());

        verify(uploadService).getUploadByIdAsDto(UPLOAD_ID, CALLER_ID, true);
    }

    @Test
    void anUploadTheCallerMayNotSeeIsA404NotA403() throws Exception {
        when(uploadService.getUploadByIdAsDto(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/" + UPLOAD_ID).with(TestAuthorities.as(Role.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void eventListingIsScopedToTheCallerForAnOrdinaryUser() throws Exception {
        Page<UploadDto> empty = new PageImpl<>(List.of());
        when(uploadService.getUploadsByEventAsDto(any(), any(), any(), anyBoolean())).thenReturn(empty);

        mockMvc.perform(get(BASE + "/event/" + EVENT_ID).with(TestAuthorities.as(Role.USER)))
                .andExpect(status().isOk());

        verify(uploadService).getUploadsByEventAsDto(eq(EVENT_ID), any(Pageable.class), eq(CALLER_ID), eq(false));
    }

    @Test
    void eventListingIsUnrestrictedForAHolderOfMediaUploadRead() throws Exception {
        Page<UploadDto> empty = new PageImpl<>(List.of());
        when(uploadService.getUploadsByEventAsDto(any(), any(), any(), anyBoolean())).thenReturn(empty);

        mockMvc.perform(get(BASE + "/event/" + EVENT_ID).with(TestAuthorities.as(Role.USER, Role.BOARD_ADMIN)))
                .andExpect(status().isOk());

        verify(uploadService).getUploadsByEventAsDto(eq(EVENT_ID), any(Pageable.class), eq(CALLER_ID), eq(true));
    }

    /** Guards the premise of the tests above: plain USER really does lack media:upload:read. */
    @Test
    void plainUserRoleDoesNotConferMediaUploadRead() {
        assertThat(Role.USER.getPermissions()).doesNotContain(Permission.MEDIA_UPLOAD_READ);
        assertThat(Role.BOARD_ADMIN.getPermissions()).contains(Permission.MEDIA_UPLOAD_READ);
    }
}
