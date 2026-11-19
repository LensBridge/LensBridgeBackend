package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.service.agent.ContentChangeNotifier;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.security.CommandAuthorizer;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.EnrollmentTokenService;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/admin/board/devices/{id}/commands} with the real {@link CommandDispatcher}:
 * the requests the admin console sends (CommandLauncher.jsx) still go through, and a TTL,
 * deadline or payload the kind does not accept is a 400 whose message names the problem.
 */
@WebMvcTest(controllers = DeviceAdminController.class)
@Import({MethodSecurityTestConfig.class, CommandDispatcher.class, DeviceAdminControllerIssueCommandTest.Authorizer.class})
class DeviceAdminControllerIssueCommandTest {

    /** Under the bean name the endpoint's @PreAuthorize expression refers to. */
    @TestConfiguration
    static class Authorizer {
        @Bean
        CommandAuthorizer commandAuthorizer() {
            return new CommandAuthorizer();
        }
    }

    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-0000-4000-8000-000000000002");
    private static final String URL = "/api/admin/board/devices/" + DEVICE_ID + "/commands";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private DeviceCommandRepository commandRepository;
    @MockitoBean
    private AgentSessionRegistry sessionRegistry;
    @MockitoBean
    private DeviceEventPublisher events;
    @MockitoBean
    private OfflineBundleService offlineBundleService;
    @MockitoBean
    private EnrollmentTokenService enrollmentTokenService;
    @MockitoBean
    private AdminAuditService auditService;
    @MockitoBean
    private ContentChangeNotifier contentChanges;
    @MockitoBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(Device.builder()
                .id(DEVICE_ID).displayName("Lobby").audience(Audience.BOTH).publicKey(new byte[32]).build()));
        when(commandRepository.save(any())).thenAnswer(inv -> {
            DeviceCommand cmd = inv.getArgument(0);
            cmd.setId(UUID.randomUUID());
            return cmd;
        });
    }

    private ResultActions issue(String body) throws Exception {
        return mockMvc.perform(post(URL)
                .with(TestAuthorities.asHolderOf(Permission.BOARD_COMMAND_BENIGN,
                        Permission.BOARD_COMMAND_DISRUPTIVE, Permission.BOARD_COMMAND_INSPECT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Exactly what CommandLauncher.jsx sends today: {} payloads and its per-kind deadlines. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"kind\":\"chrome.reload\",\"payload\":{},\"deadlineMs\":30000}",
            "{\"kind\":\"config.refresh\",\"payload\":{},\"deadlineMs\":30000}",
            "{\"kind\":\"chrome.screenshot\",\"payload\":{},\"deadlineMs\":30000}",
            "{\"kind\":\"kiosk.restart\",\"payload\":{},\"deadlineMs\":30000}",
            "{\"kind\":\"system.reboot\",\"payload\":{},\"deadlineMs\":30000}",
            "{\"kind\":\"update.install_now\",\"payload\":{},\"deadlineMs\":600000}",
            "{\"kind\":\"logs.tail\",\"payload\":{\"lines\":100},\"deadlineMs\":45000}",
            "{\"kind\":\"logs.tail\",\"payload\":{\"lines\":500},\"deadlineMs\":45000}"})
    void theAdminConsolesRequestsAreAccepted(String body) throws Exception {
        issue(body).andExpect(status().isAccepted());
    }

    @Test
    void aRebootWithAYearLongTtlIsABadRequest() throws Exception {
        issue("{\"kind\":\"system.reboot\",\"ttlSeconds\":31536000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "ttlSeconds: ttlSeconds must be at most 3600; disruptive commands allow less"));
        verify(commandRepository, never()).save(any());
    }

    @Test
    void aRebootTtlOverItsOwnLimitIsABadRequest() throws Exception {
        issue("{\"kind\":\"system.reboot\",\"ttlSeconds\":3600}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ttlSeconds for system.reboot must be from 1 to 600"));
        verify(commandRepository, never()).save(any());
    }

    @Test
    void aDeadlineOverTheCapIsABadRequest() throws Exception {
        issue("{\"kind\":\"update.install_now\",\"deadlineMs\":86400000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("deadlineMs: deadlineMs must be at most 900000"));
    }

    @Test
    void aPayloadOnAParameterlessKindIsABadRequest() throws Exception {
        issue("{\"kind\":\"kiosk.restart\",\"payload\":{\"unit\":\"ssh.service\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("kiosk.restart takes no payload"));
        verify(commandRepository, never()).save(any());
    }

    @Test
    void logsTailOutsideItsBoundsIsABadRequest() throws Exception {
        issue("{\"kind\":\"logs.tail\",\"payload\":{\"lines\":999999999}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("logs.tail lines must be a whole number from 1 to 500"));
    }
}
