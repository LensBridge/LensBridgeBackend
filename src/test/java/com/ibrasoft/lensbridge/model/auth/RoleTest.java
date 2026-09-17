package com.ibrasoft.lensbridge.model.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void getAuthorityPrependsRolePrefixForUser() {
        assertThat(Role.USER.getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void getAuthorityPrependsRolePrefixForAdmin() {
        assertThat(Role.ADMIN.getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void getAuthorityPrependsRolePrefixForRoot() {
        assertThat(Role.ROOT.getAuthority()).isEqualTo("ROLE_ROOT");
    }

    @Test
    void authorityConstantsMatchEnumNamesWithoutPrefix() {
        assertThat(Role.Authority.USER).isEqualTo("USER");
        assertThat(Role.Authority.ADMIN).isEqualTo("ADMIN");
        assertThat(Role.Authority.ROOT).isEqualTo("ROOT");
    }

    @Test
    void authorityConstantsAreConsistentWithEnumNames() {
        assertThat(Role.Authority.USER).isEqualTo(Role.USER.name());
        assertThat(Role.Authority.ADMIN).isEqualTo(Role.ADMIN.name());
        assertThat(Role.Authority.ROOT).isEqualTo(Role.ROOT.name());
    }
}
