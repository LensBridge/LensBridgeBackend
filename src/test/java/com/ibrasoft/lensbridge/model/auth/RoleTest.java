package com.ibrasoft.lensbridge.model.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void getAuthorityPrependsRolePrefixForUser() {
        assertThat(Role.USER.getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void getAuthorityPrependsRolePrefixForRoot() {
        assertThat(Role.ROOT.getAuthority()).isEqualTo("ROLE_ROOT");
    }

    @Test
    void authorityConstantsMatchEnumNames() {
        assertThat(Role.Authority.USER).isEqualTo("USER");
        assertThat(Role.Authority.ROOT).isEqualTo("ROOT");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void authorityConstantMatchesEnumName(Role role) {
        assertThat(role.getAuthority()).isEqualTo("ROLE_" + role.name());
    }
}
