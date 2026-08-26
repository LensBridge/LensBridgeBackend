package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.minbar.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.minbar.board.DeviceCommandStatus;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The command-history endpoint is gated on {@link Permission#BOARD_DEVICE_READ}, which is
 * fleet status and handed out freely. The {@code output} of a command is not fleet status:
 * for {@code chrome.screenshot} it is an image of a physical display and for {@code logs.tail}
 * it is agent internals, which the live STOMP channel already gates behind
 * {@link Permission#BOARD_TELEMETRY_SUBSCRIBE}. These tests pin the carve-out so history replay
 * cannot be used to walk around the live-subscription check.
 * <p>
 * Direct per-user permission grants exist ({@code AuthorityResolver#directGrants}), so a
 * principal holding only {@code BOARD_DEVICE_READ} is a reachable state, not a hypothetical.
 */
@WebMvcTest(controllers = DeviceAdminController.class)
@Import(MethodSecurityTestConfig.class)
class DeviceAdminControllerCommandOutputTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String SCREENSHOT_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAO=";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceCommandRepository commandRepository;
    @MockitoBean
    private EnrollmentTokenService enrollmentTokenService;
    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private CommandDispatcher commandDispatcher;
    @MockitoBean
    private AgentSessionRegistry sessionRegistry;
    @MockitoBean
    private AdminAuditService auditService;

    /** Not used here; WebConfig's CurrentUserArgumentResolver needs it and the slice omits the service layer. */
    @MockitoBean
    private UserService userService;

    private String url() {
        return "/api/admin/board/devices/" + DEVICE_ID + "/commands";
    }

    private void haveScreenshotHistory() {
        when(commandRepository.findTop50ByDeviceIdOrderByIssuedAtDesc(DEVICE_ID))
                .thenReturn(List.of(screenshot()));
    }

    private static DeviceCommand screenshot() {
        return DeviceCommand.builder()
                .id(UUID.randomUUID())
                .deviceId(DEVICE_ID)
                .kind("chrome.screenshot")
                .payloadJson("{\"kind\":\"chrome.screenshot\"}")
                .issuedBy("admin@example.com")
                .status(DeviceCommandStatus.SUCCEEDED)
                .issuedAt(Instant.parse("2026-08-25T10:00:00Z"))
                .finishedAt(Instant.parse("2026-08-25T10:00:04Z"))
                .outputJson("{\"imageBase64\":\"" + SCREENSHOT_PNG + "\"}")
                .build();
    }

    @Test
    void fleetReaderSeesCommandMetadataButNotTheScreenshot() throws Exception {
        haveScreenshotHistory();

        mockMvc.perform(get(url()).with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(status().isOk())
                // Fleet status stays visible - that is what BOARD_DEVICE_READ is for.
                .andExpect(jsonPath("$[0].kind").value("chrome.screenshot"))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].issuedBy").value("admin@example.com"))
                .andExpect(jsonPath("$[0].finishedAt").exists())
                // The captured display is not.
                .andExpect(jsonPath("$[0].output").doesNotExist())
                .andExpect(jsonPath("$[0].outputRedacted").value(true));
    }

    @Test
    void screenshotBytesNeverAppearAnywhereInAFleetReaderResponse() throws Exception {
        haveScreenshotHistory();

        String body = mockMvc.perform(get(url())
                        .with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        if (body.contains(SCREENSHOT_PNG)) {
            throw new AssertionError("Screenshot bytes leaked to a BOARD_DEVICE_READ-only caller: " + body);
        }
    }

    @Test
    void telemetrySubscriberSeesTheScreenshot() throws Exception {
        haveScreenshotHistory();

        mockMvc.perform(get(url()).with(TestAuthorities.asHolderOf(
                        Permission.BOARD_DEVICE_READ, Permission.BOARD_TELEMETRY_SUBSCRIBE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].output.imageBase64").value(SCREENSHOT_PNG))
                .andExpect(jsonPath("$[0].outputRedacted").value(false));
    }

    @Test
    void aCommandWithNoOutputIsNotReportedAsRedacted() throws Exception {
        DeviceCommand pending = DeviceCommand.builder()
                .id(UUID.randomUUID())
                .deviceId(DEVICE_ID)
                .kind("chrome.reload")
                .issuedBy("admin@example.com")
                .status(DeviceCommandStatus.PENDING)
                .issuedAt(Instant.parse("2026-08-25T10:00:00Z"))
                .build();
        when(commandRepository.findTop50ByDeviceIdOrderByIssuedAtDesc(DEVICE_ID))
                .thenReturn(List.of(pending));

        mockMvc.perform(get(url()).with(TestAuthorities.asHolderOf(Permission.BOARD_DEVICE_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].output").doesNotExist())
                .andExpect(jsonPath("$[0].outputRedacted").value(false));
    }

    /** The endpoint gate itself is unchanged: telemetry alone does not grant fleet history. */
    @Test
    void telemetryAloneStillCannotReachTheEndpoint() throws Exception {
        mockMvc.perform(get(url()).with(TestAuthorities.asHolderOf(Permission.BOARD_TELEMETRY_SUBSCRIBE)))
                .andExpect(status().isForbidden());
    }
}
