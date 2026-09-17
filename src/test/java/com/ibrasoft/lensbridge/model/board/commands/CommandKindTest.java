package com.ibrasoft.lensbridge.model.board.commands;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.ibrasoft.lensbridge.model.auth.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CommandKindTest {

    /**
     * The registry and the Jackson subtypes must describe the same set of commands. A kind
     * registered with Jackson but missing here would be unclassifiable and therefore
     * unauthorizable; a kind here but not with Jackson could never be deserialized.
     */
    @Test
    void wireNamesMatchTheJacksonSubtypeRegistration() {
        JsonSubTypes subTypes = CommandPayload.class.getAnnotation(JsonSubTypes.class);
        assertThat(subTypes).as("CommandPayload must remain @JsonSubTypes-annotated").isNotNull();

        Set<String> jackson = Arrays.stream(subTypes.value())
                .map(JsonSubTypes.Type::name)
                .collect(Collectors.toSet());
        Set<String> registry = Arrays.stream(CommandKind.values())
                .map(CommandKind::getWireName)
                .collect(Collectors.toSet());

        assertThat(registry).isEqualTo(jackson);
    }

    @ParameterizedTest
    @EnumSource(CommandKind.class)
    void everyKindRoundTripsThroughItsWireName(CommandKind kind) {
        assertThat(CommandKind.from(kind.getWireName())).contains(kind);
    }

    @Test
    void unknownAndNullKindsResolveToEmpty() {
        assertThat(CommandKind.from("chrome.explode")).isEmpty();
        assertThat(CommandKind.from(null)).isEmpty();
        assertThat(CommandKind.from("")).isEmpty();
    }

    // ==================== Risk classification ====================
    //
    // These assertions are the security boundary, not documentation. Reclassifying a command
    // silently changes who can run it, so each mapping is pinned individually.

    @Test
    void reloadAndConfigRefreshAreBenign() {
        assertThat(CommandKind.CHROME_RELOAD.getRisk()).isEqualTo(CommandRisk.BENIGN);
        assertThat(CommandKind.CONFIG_REFRESH.getRisk()).isEqualTo(CommandRisk.BENIGN);
    }

    @Test
    void restartAndRebootAreDisruptive() {
        assertThat(CommandKind.KIOSK_RESTART.getRisk()).isEqualTo(CommandRisk.DISRUPTIVE);
        assertThat(CommandKind.SYSTEM_REBOOT.getRisk()).isEqualTo(CommandRisk.DISRUPTIVE);
    }

    /** Both return device-side data to the caller. Neither may ride along with a reboot grant. */
    @Test
    void screenshotAndLogTailAreInspect() {
        assertThat(CommandKind.CHROME_SCREENSHOT.getRisk()).isEqualTo(CommandRisk.INSPECT);
        assertThat(CommandKind.LOGS_TAIL.getRisk()).isEqualTo(CommandRisk.INSPECT);
    }

    @Test
    void eachRiskClassMapsToItsOwnPermission() {
        assertThat(CommandRisk.BENIGN.getRequiredPermission())
                .isEqualTo(Permission.BOARD_COMMAND_BENIGN);
        assertThat(CommandRisk.DISRUPTIVE.getRequiredPermission())
                .isEqualTo(Permission.BOARD_COMMAND_DISRUPTIVE);
        assertThat(CommandRisk.INSPECT.getRequiredPermission())
                .isEqualTo(Permission.BOARD_COMMAND_INSPECT);

        Set<Permission> distinct = Arrays.stream(CommandRisk.values())
                .map(CommandRisk::getRequiredPermission)
                .collect(Collectors.toSet());
        assertThat(distinct).hasSameSizeAs(CommandRisk.values());
    }

    @Test
    void screenshotRequiresInspectRatherThanDisruptive() {
        Optional<CommandKind> screenshot = CommandKind.from("chrome.screenshot");
        assertThat(screenshot).isPresent();
        assertThat(screenshot.get().getRequiredPermission())
                .isEqualTo(Permission.BOARD_COMMAND_INSPECT);
    }
}
