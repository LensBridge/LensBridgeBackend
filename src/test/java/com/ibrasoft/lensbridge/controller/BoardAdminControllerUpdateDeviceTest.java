package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.request.UpdateDeviceRequest;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.PromotableSocialMediaService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request binding and validation for PATCH /api/admin/board/devices/{deviceId}.
 * <p>
 * The endpoint carried {@code @Valid} from the start, but {@link UpdateDeviceRequest}
 * declared no constraints, so it validated nothing: a blank {@code displayName} reached
 * {@code Device.displayName}, whose column is {@code nullable = false} but says nothing
 * about emptiness. The board then showed as unnamed in every admin listing. These tests
 * pin both halves — the constraints, and the {@code @Valid} that runs them.
 */
@WebMvcTest(controllers = BoardAdminController.class)
@Import(MethodSecurityTestConfig.class)
class BoardAdminControllerUpdateDeviceTest {

    private static final String URL = "/api/admin/board/devices/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;
    @MockitoBean
    private PosterService posterService;
    @MockitoBean
    private PromotableSocialMediaService socialMediaService;
    @MockitoBean
    private AdminAuditService auditService;
    @MockitoBean
    private BoardStreamHandler boardStreamHandler;

    /** WebConfig registers CurrentUserArgumentResolver, which needs this in the slice. */
    @MockitoBean
    private UserService userService;

    private static final UUID DEVICE_ID = UUID.randomUUID();

    private void patchBody(String json, int expectedStatus) throws Exception {
        mockMvc.perform(patch(URL + DEVICE_ID)
                        .with(TestAuthorities.as(Role.BOARD_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void rejectsAnEmptyDisplayName() throws Exception {
        mockMvc.perform(patch(URL + DEVICE_ID)
                        .with(TestAuthorities.as(Role.BOARD_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("displayName")));

        verify(boardService, never()).updateDevice(any(), any());
    }

    /** @Size(min=1) would have let this through; the blank check is what catches it. */
    @Test
    void rejectsAWhitespaceOnlyDisplayName() throws Exception {
        patchBody("{\"displayName\":\"   \"}", 400);

        verify(boardService, never()).updateDevice(any(), any());
    }

    @Test
    void rejectsADisplayNameLongerThanTheColumn() throws Exception {
        patchBody("{\"displayName\":\"" + "x".repeat(256) + "\"}", 400);

        verify(boardService, never()).updateDevice(any(), any());
    }

    /** Null means "leave it alone" on a PATCH, so an audience-only body must pass. */
    @Test
    void acceptsAnOmittedDisplayName() throws Exception {
        when(boardService.updateDevice(eq(DEVICE_ID), any()))
                .thenReturn(Device.builder().id(DEVICE_ID).displayName("Musallah A")
                        .audience(Audience.BOTH).build());

        patchBody("{\"audience\":\"both\"}", 200);

        verify(boardService).updateDevice(eq(DEVICE_ID), any());
    }

    @Test
    void acceptsARealDisplayName() throws Exception {
        when(boardService.updateDevice(eq(DEVICE_ID), any()))
                .thenReturn(Device.builder().id(DEVICE_ID).displayName("Musallah A")
                        .audience(Audience.BOTH).build());

        patchBody("{\"displayName\":\"Musallah A\"}", 200);

        verify(boardService).updateDevice(eq(DEVICE_ID), any());
    }
}
