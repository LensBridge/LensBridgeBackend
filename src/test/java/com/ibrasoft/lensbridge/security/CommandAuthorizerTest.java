package com.ibrasoft.lensbridge.security;

import com.ibrasoft.lensbridge.dto.board.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandAuthorizerTest {

    private final CommandAuthorizer authorizer = new CommandAuthorizer();

    private static Authentication withPermissions(Permission... permissions) {
        List<GrantedAuthority> authorities = java.util.Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.getAuthority()))
                .toList();
        return new UsernamePasswordAuthenticationToken("u@example.com", null, authorities);
    }

    private static Authentication withRole(Role role) {
        List<GrantedAuthority> authorities = role.getPermissions().stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.getAuthority()))
                .toList();
        return new UsernamePasswordAuthenticationToken("u@example.com", null, authorities);
    }

    private static IssueCommandRequest command(String kind) {
        return new IssueCommandRequest(kind, null, null, null);
    }

    @Test
    void benignPermissionAllowsReload() {
        assertThat(authorizer.canIssue(withPermissions(Permission.BOARD_COMMAND_BENIGN),
                command("chrome.reload"))).isTrue();
    }

    @Test
    void benignPermissionDoesNotAllowReboot() {
        assertThat(authorizer.canIssue(withPermissions(Permission.BOARD_COMMAND_BENIGN),
                command("system.reboot"))).isFalse();
    }

    @Test
    void disruptivePermissionDoesNotAllowScreenshot() {
        assertThat(authorizer.canIssue(withPermissions(Permission.BOARD_COMMAND_DISRUPTIVE),
                command("chrome.screenshot"))).isFalse();
    }

    @Test
    void inspectPermissionAllowsScreenshotAndLogTail() {
        Authentication auth = withPermissions(Permission.BOARD_COMMAND_INSPECT);
        assertThat(authorizer.canIssue(auth, command("chrome.screenshot"))).isTrue();
        assertThat(authorizer.canIssue(auth, command("logs.tail"))).isTrue();
    }

    @Test
    void boardEditorMayIssueNoCommandsAtAll() {
        Authentication editor = withRole(Role.BOARD_EDITOR);
        for (String kind : List.of("chrome.reload", "config.refresh", "kiosk.restart",
                                   "system.reboot", "chrome.screenshot", "logs.tail")) {
            assertThat(authorizer.canIssue(editor, command(kind)))
                    .as("editor must not be able to issue %s", kind)
                    .isFalse();
        }
    }

    @Test
    void boardAdminMayIssueEverything() {
        Authentication boardAdmin = withRole(Role.BOARD_ADMIN);
        for (String kind : List.of("chrome.reload", "config.refresh", "kiosk.restart",
                                   "system.reboot", "chrome.screenshot", "logs.tail")) {
            assertThat(authorizer.canIssue(boardAdmin, command(kind)))
                    .as("board admin must be able to issue %s", kind)
                    .isTrue();
        }
    }

    /**
     * An unrecognised kind is passed through so CommandDispatcher can answer 400. A typo
     * deserves "no such command", not "you are not allowed" — and nothing is issued either way.
     */
    @Test
    void unknownKindIsLeftForTheDispatcherToRejectAsBadRequest() {
        assertThat(authorizer.canIssue(withPermissions(), command("chrome.explode"))).isTrue();
    }

    @Test
    void noAuthenticationIsDenied() {
        assertThat(authorizer.canIssue(null, command("chrome.reload"))).isFalse();
    }

    @Test
    void nullRequestIsDenied() {
        assertThat(authorizer.canIssue(withPermissions(Permission.BOARD_COMMAND_BENIGN), null))
                .isFalse();
    }

    @Test
    void unauthenticatedTokenIsDenied() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        anonymous.setAuthenticated(false);
        assertThat(authorizer.canIssue(anonymous, command("chrome.reload"))).isFalse();
    }
}
