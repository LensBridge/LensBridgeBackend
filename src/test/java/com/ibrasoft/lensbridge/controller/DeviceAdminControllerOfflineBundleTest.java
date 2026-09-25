package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundle;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/admin/board/devices/{id}/offline-bundle}: who may download it, and that the
 * HTTP shape (content type, filename, error bodies) matches the signed-package contract
 * (MusallahBoard {@code agent/docs/architecture.md}, sections 4.3 and 9.3).
 */
@WebMvcTest(controllers = DeviceAdminController.class)
@Import(MethodSecurityTestConfig.class)
class DeviceAdminControllerOfflineBundleTest {

    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-0000-4000-8000-000000000001");
    private static final String URL = "/api/admin/board/devices/" + DEVICE_ID + "/offline-bundle";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OfflineBundleService offlineBundleService;
    @MockitoBean
    private EnrollmentTokenService enrollmentTokenService;
    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private DeviceCommandRepository commandRepository;
    @MockitoBean
    private CommandDispatcher commandDispatcher;
    @MockitoBean
    private AgentSessionRegistry sessionRegistry;
    @MockitoBean
    private AdminAuditService auditService;

    /** Needed by WebConfig's CurrentUserArgumentResolver; see BoardAdminControllerPermissionTest. */
    @MockitoBean
    private UserService userService;

    private static OfflineBundle bundle() {
        return new OfflineBundle("musallahboard-content-3f2a1b4c-2026-09-24.mbu", List.of(
                new OfflineBundle.Entry("mbu.json", "{}".getBytes(StandardCharsets.UTF_8), false),
                new OfflineBundle.Entry("mbu.sig", "{}".getBytes(StandardCharsets.UTF_8), false)));
    }

    @Test
    @WithAnonymousUser
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(offlineBundleService);
    }

    @Test
    void holderOfOtherBoardPermissionsIsForbidden() throws Exception {
        mockMvc.perform(get(URL).with(TestAuthorities.asHolderOf(
                        Permission.BOARD_CONTENT_READ, Permission.BOARD_CONFIG_READ)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(offlineBundleService);
    }

    @Test
    void deviceReadAloneIsEnoughAndStreamsThePackage() throws Exception {
        when(offlineBundleService.build(DEVICE_ID, 14)).thenReturn(bundle());

        MvcResult result = mockMvc.perform(get(URL).with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.musallahboard.mbu"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"musallahboard-content-3f2a1b4c-2026-09-24.mbu\""))
                .andReturn();

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("mbu.json");
            assertThat(zip.getNextEntry().getName()).isEqualTo("mbu.sig");
        }
        verify(offlineBundleService).build(DEVICE_ID, 14);
    }

    @Test
    void boardViewerMayDownload() throws Exception {
        when(offlineBundleService.build(any(), anyInt())).thenReturn(bundle());

        mockMvc.perform(get(URL).param("days", "7").with(TestAuthorities.as(Role.BOARD_VIEWER)))
                .andExpect(status().isOk());
        verify(offlineBundleService).build(DEVICE_ID, 7);
    }

    /** Errors must come back as JSON even though the success body is a zip. */
    @Test
    void serviceErrorsKeepTheirStatusAndJsonMessage() throws Exception {
        when(offlineBundleService.build(DEVICE_ID, 14)).thenThrow(new ApiResponseException(
                HttpStatus.BAD_GATEWAY,
                ErrorResponse.of("Could not fetch the image for poster \"Eid\" (poster:1): NoSuchKey")));

        mockMvc.perform(get(URL).with(TestAuthorities.as(Role.BOARD_VIEWER)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "Could not fetch the image for poster \"Eid\" (poster:1): NoSuchKey"));
    }

    @Test
    void anUnconfiguredSigningKeyIsA503WithJsonMessage() throws Exception {
        when(offlineBundleService.build(DEVICE_ID, 14)).thenThrow(new ApiResponseException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorResponse.of("Content signing is not configured on this server")));

        mockMvc.perform(get(URL).with(TestAuthorities.as(Role.BOARD_VIEWER)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Content signing is not configured on this server"));
    }

    @Test
    void nonNumericDaysIsABadRequest() throws Exception {
        mockMvc.perform(get(URL).param("days", "two-weeks").with(TestAuthorities.as(Role.BOARD_VIEWER)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(offlineBundleService);
    }
}
