package com.ibrasoft.lensbridge.model.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The authorization policy, asserted. Every test here is a property the design depends on;
 * if one fails, someone widened a bundle and should have to say so out loud.
 */
class RolePermissionsTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleHasABundle(Role role) {
        assertThat(role.getPermissions()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void bundlesAreImmutable(Role role) {
        assertThatThrownBy(() -> role.getPermissions().add(Permission.IAM_ROLE_GRANT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rootHoldsEverything() {
        assertThat(Role.ROOT.getPermissions())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
    }

    @Test
    void onlyRootCanGrantRoles() {
        for (Role role : Role.values()) {
            if (role == Role.ROOT) continue;
            assertThat(role.getPermissions())
                    .as("%s must not confer the escalation permission", role)
                    .doesNotContain(Permission.IAM_ROLE_GRANT);
        }
    }

    // ==================== Legacy media, unchanged ====================

    @Test
    void adminContainsUser() {
        assertThat(Role.ADMIN.getPermissions()).containsAll(Role.USER.getPermissions());
    }

    @Test
    void userCanOnlyManageItsOwnUploads() {
        assertThat(Role.USER.getPermissions()).containsExactly(Permission.MEDIA_UPLOAD_SELF);
    }

    @Test
    void mediaModerationStaysOutOfEveryBoardRole() {
        for (Role role : new Role[]{Role.BOARD_VIEWER, Role.BOARD_EDITOR,
                                    Role.BOARD_ADMIN}) {
            assertThat(role.getPermissions())
                    .as("%s must not confer media moderation", role)
                    .doesNotContain(Permission.MEDIA_UPLOAD_MODERATE);
        }
    }

    // ==================== MusallahBoard ====================

    @Test
    void editorAndOperatorBothContainViewer() {
        assertThat(Role.BOARD_EDITOR.getPermissions()).containsAll(Role.BOARD_VIEWER.getPermissions());
    }

    @Test
    void viewerCanChangeNothing() {
        assertThat(Role.BOARD_VIEWER.getPermissions())
                .allSatisfy(p -> assertThat(p.getAuthority())
                        .matches(".*(:read|:subscribe)$"));
    }

    @Test
    void editorCanChangeContentButNotTheBoardItself() {
        assertThat(Role.BOARD_EDITOR.getPermissions())
                .contains(Permission.BOARD_POSTER_WRITE,
                          Permission.BOARD_EVENT_WRITE,
                          Permission.BOARD_WEEKLY_WRITE,
                          Permission.BOARD_TICKER_WRITE)
                .doesNotContain(Permission.BOARD_CONFIG_WRITE,
                                Permission.BOARD_COMMAND_BENIGN,
                                Permission.BOARD_COMMAND_DISRUPTIVE,
                                Permission.BOARD_COMMAND_INSPECT,
                                Permission.BOARD_DEVICE_ENROLL,
                                Permission.BOARD_DEVICE_REVOKE);
    }

    /** The whole point of splitting the ticker out of the config. */
    @Test
    void editorCanWriteTickerCopyWithoutConfigWrite() {
        assertThat(Role.BOARD_EDITOR.getPermissions())
                .contains(Permission.BOARD_TICKER_WRITE)
                .doesNotContain(Permission.BOARD_CONFIG_WRITE);
    }

    @Test
    void boardAdminSubsumesEditorAndOperator() {
        assertThat(Role.BOARD_ADMIN.getPermissions())
                .containsAll(Role.BOARD_EDITOR.getPermissions())
                .contains(Permission.BOARD_DEVICE_ENROLL,
                          Permission.BOARD_DEVICE_REVOKE,
                          Permission.BOARD_COMMAND_INSPECT);
    }

    /** Owning the boards is not owning the org's accounts. */
    @Test
    void boardAdminHoldsNoIdentityPermissions() {
        assertThat(Role.BOARD_ADMIN.getPermissions())
                .doesNotContain(Permission.IAM_USER_READ,
                                Permission.IAM_USER_WRITE,
                                Permission.IAM_ROLE_GRANT);
    }
}
