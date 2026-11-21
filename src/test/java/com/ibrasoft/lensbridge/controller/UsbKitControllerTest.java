package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.board.offline.SetupFileService;
import com.ibrasoft.lensbridge.service.board.offline.UsbKitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsbKitController.class)
@Import(MethodSecurityTestConfig.class)
class UsbKitControllerTest {

    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private UsbKitService usbKitService;
    @MockitoBean private DeviceRepository deviceRepository;
    @MockitoBean private AdminAuditService auditService;
    @MockitoBean private SetupFileService setupFileService;
    @MockitoBean private UserService userService;

    private UsbKitService.Kit kit(String filename, List<String> omitted) throws Exception {
        Path file = Files.createTempFile("usb-kit-test-", ".zip");
        Files.write(file, new byte[]{'P', 'K', 5, 6});
        return new UsbKitService.Kit(file, filename, omitted);
    }

    @Test
    void readingDevicesIsEnoughForAContentKit() throws Exception {
        Device device = Device.builder().id(DEVICE_ID).displayName("North").audience(Audience.BOTH).build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        UsbKitService.Kit kit = kit("musallahboard-usb-kit-3f2a1b4c-2026-11-19.zip", List.of());
        when(usbKitService.build(eq(List.of(device)), anyString(), eq(31), eq(false), eq(false))).thenReturn(kit);

        byte[] body = mockMvc.perform(get("/api/admin/board/devices/" + DEVICE_ID + "/usb-kit").param("days", "31")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith(
                        "attachment; filename=\"musallahboard-usb-kit-3f2a1b4c-")))
                .andExpect(header().doesNotExist(UsbKitController.OMITTED_HEADER))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).containsExactly('P', 'K', 5, 6);
        assertThat(Files.exists(kit.file())).as("temp file removed").isFalse();
        verifyNoInteractions(auditService);
    }

    /** Setup files can carry a claim secret. */
    @Test
    void setupFilesInAKitNeedTheEnrollPermission() throws Exception {
        mockMvc.perform(get("/api/admin/board/usb-kit").param("includeSetup", "true")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usbKitService);

        Device a = Device.builder().id(DEVICE_ID).displayName("North").audience(Audience.BOTH).build();
        when(deviceRepository.findByRevokedAtIsNullOrderByDisplayNameAsc()).thenReturn(List.of(a));
        when(usbKitService.build(any(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(kit("musallahboard-usb-kit-all-2026-11-19.zip", List.of("agent: could not fetch")));

        mockMvc.perform(get("/api/admin/board/usb-kit").param("includeSetup", "true").param("online", "true")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ, Permission.BOARD_DEVICE_ENROLL)))
                .andExpect(status().isOk())
                .andExpect(header().string(UsbKitController.OMITTED_HEADER, "agent: could not fetch"));
        verify(usbKitService).build(eq(List.of(a)), anyString(), eq(14), eq(true), eq(true));
    }

    @Test
    void anUnknownBoardIsA404() throws Exception {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/admin/board/devices/" + DEVICE_ID + "/usb-kit")
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(status().isNotFound());
    }
}
