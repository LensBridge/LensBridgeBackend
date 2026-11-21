package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.request.CreateDeviceRequest;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.ContentChangeNotifier;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundle;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import com.ibrasoft.lensbridge.service.board.offline.SetupFileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Add board" and the setup file (contracts C3, C4): request shape and who may do it. */
@WebMvcTest(controllers = DeviceAdminController.class)
@Import(MethodSecurityTestConfig.class)
class DeviceAdminControllerSetupTest {

    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-0000-4000-8000-000000000001");
    private static final String DEVICES = "/api/admin/board/devices";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private OfflineBundleService offlineBundleService;
    @MockitoBean private EnrollmentTokenService enrollmentTokenService;
    @MockitoBean private DeviceRepository deviceRepository;
    @MockitoBean private DeviceCommandRepository commandRepository;
    @MockitoBean private CommandDispatcher commandDispatcher;
    @MockitoBean private AgentSessionRegistry sessionRegistry;
    @MockitoBean private AdminAuditService auditService;
    @MockitoBean private BoardService boardService;
    @MockitoBean private ContentChangeNotifier contentChanges;
    @MockitoBean private SetupFileService setupFileService;
    @MockitoBean private UserService userService;

    @Test
    void createsABoardWithNoKey() throws Exception {
        when(boardService.createDevice(any())).thenReturn(Device.builder()
                .id(DEVICE_ID).displayName("North Musallah").audience(Audience.BROTHERS).build());

        mockMvc.perform(post(DEVICES).with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"North Musallah\",\"audience\":\"brothers\","
                                + "\"location\":{\"city\":\"Toronto\",\"latitude\":43.65,\"longitude\":-79.38,"
                                + "\"timezone\":\"America/Toronto\",\"method\":\"ISNA\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("North Musallah"))
                .andExpect(jsonPath("$.enrolledAt").doesNotExist());

        ArgumentCaptor<CreateDeviceRequest> sent = ArgumentCaptor.forClass(CreateDeviceRequest.class);
        verify(boardService).createDevice(sent.capture());
        assertThat(sent.getValue().getLocation().getCity()).isEqualTo("Toronto");
        verify(auditService).logAuditEvent(anyString(), eq(AuditAction.UPDATE_BOARD_CONFIG), eq("Device"),
                eq(DEVICE_ID), any(), eq("created board"));
    }

    @Test
    void creatingNeedsANameAndAnAudience() throws Exception {
        for (String body : List.of("{\"audience\":\"both\"}", "{\"displayName\":\"  \",\"audience\":\"both\"}",
                "{\"displayName\":\"A\"}")) {
            mockMvc.perform(post(DEVICES).with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(boardService);
    }

    @Test
    void creatingNeedsTheEnrollPermission() throws Exception {
        mockMvc.perform(post(DEVICES).with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ,
                                Permission.BOARD_CONFIG_WRITE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"A\",\"audience\":\"both\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(boardService);
    }

    @Test
    void theSetupFileIsOfflineByDefaultWithoutABackendUrl() throws Exception {
        when(setupFileService.build(DEVICE_ID, false)).thenReturn(new OfflineBundle("musallahboard-setup-3f2a1b4c.mbu",
                List.of(new OfflineBundle.Entry("mbu.json", "{}".getBytes(StandardCharsets.UTF_8), false))));

        mockMvc.perform(get(DEVICES + "/" + DEVICE_ID + "/setup-file")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.musallahboard.mbu"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"musallahboard-setup-3f2a1b4c.mbu\""));
        verify(setupFileService).build(DEVICE_ID, false);
        verify(auditService).logAuditEvent(anyString(), eq(AuditAction.ISSUE_ENROLLMENT_TOKEN), eq("Device"),
                eq(DEVICE_ID), any(), eq("setup file (offline)"));
    }

    @Test
    void onlineAsksForTheOnlineFile() throws Exception {
        when(setupFileService.build(DEVICE_ID, true)).thenReturn(new OfflineBundle("musallahboard-setup-3f2a1b4c.mbu",
                List.of(new OfflineBundle.Entry("mbu.json", "{}".getBytes(StandardCharsets.UTF_8), false))));

        mockMvc.perform(get(DEVICES + "/" + DEVICE_ID + "/setup-file").param("online", "true")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL)))
                .andExpect(status().isOk());
        verify(setupFileService).build(DEVICE_ID, true);
    }

    @Test
    void theSetupFileIsOnlineByDefaultWithABackendUrl() throws Exception {
        when(setupFileService.onlineByDefault()).thenReturn(true);
        when(setupFileService.build(eq(DEVICE_ID), anyBoolean())).thenReturn(new OfflineBundle("musallahboard-setup-3f2a1b4c.mbu",
                List.of(new OfflineBundle.Entry("mbu.json", "{}".getBytes(StandardCharsets.UTF_8), false))));

        mockMvc.perform(get(DEVICES + "/" + DEVICE_ID + "/setup-file")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL)))
                .andExpect(status().isOk());
        verify(setupFileService).build(DEVICE_ID, true);

        // Asking for an offline one still gets one.
        mockMvc.perform(get(DEVICES + "/" + DEVICE_ID + "/setup-file").param("online", "false")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL)))
                .andExpect(status().isOk());
        verify(setupFileService).build(DEVICE_ID, false);
    }

    @Test
    void setupDefaultsSayWhetherFilesAreOnline() throws Exception {
        when(setupFileService.onlineByDefault()).thenReturn(true);
        mockMvc.perform(get(DEVICES + "/setup-defaults")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineSetup").value(true));
        mockMvc.perform(get(DEVICES + "/setup-defaults"))
                .andExpect(status().is4xxClientError());
    }

    /** It can carry a claim secret: reading devices is not enough. */
    @Test
    void theSetupFileNeedsTheEnrollPermission() throws Exception {
        mockMvc.perform(get(DEVICES + "/" + DEVICE_ID + "/setup-file")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(setupFileService);
    }

    @Test
    void aTokenForThisBoardIsIssuedForItsRow() throws Exception {
        Device device = Device.builder().id(DEVICE_ID).displayName("North Musallah").audience(Audience.BOTH).build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        EnrollmentToken token = EnrollmentToken.builder().id(UUID.randomUUID()).deviceId(DEVICE_ID)
                .expiresAt(Instant.parse("2026-11-19T12:30:00Z")).build();
        when(enrollmentTokenService.issueFor(eq(device), eq(60), anyString()))
                .thenReturn(new EnrollmentTokenService.Issued(token, "plain-token"));

        mockMvc.perform(post(DEVICES + "/" + DEVICE_ID + "/enrollment-tokens").param("expiresInMinutes", "60")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("plain-token"))
                .andExpect(jsonPath("$.tokenId").value(token.getId().toString()));
    }

    @Test
    void noTokenForAnUnknownOrRevokedBoard() throws Exception {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        mockMvc.perform(post(DEVICES + "/" + DEVICE_ID + "/enrollment-tokens")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL)))
                .andExpect(status().isNotFound());

        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(Device.builder().id(DEVICE_ID)
                .displayName("A").audience(Audience.BOTH).revokedAt(Instant.now()).build()));
        mockMvc.perform(post(DEVICES + "/" + DEVICE_ID + "/enrollment-tokens")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_ENROLL)))
                .andExpect(status().isConflict());
        verifyNoInteractions(enrollmentTokenService);
    }
}
