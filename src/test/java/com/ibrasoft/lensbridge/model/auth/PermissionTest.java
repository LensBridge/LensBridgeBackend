package com.ibrasoft.lensbridge.model.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    @ParameterizedTest
    @EnumSource(Permission.class)
    void authorityIsNamespacedAndCarriesNoRolePrefix(Permission permission) {
        // hasAuthority() matches literally. A ROLE_ prefix here would make the permission
        // indistinguishable from a role authority in the same collection.
        assertThat(permission.getAuthority())
                .doesNotStartWith("ROLE_")
                .matches("[a-z]+(:[a-z]+)+");
    }

    @Test
    void authoritiesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Permission permission : Permission.values()) {
            assertThat(seen.add(permission.getAuthority()))
                    .as("duplicate authority string: %s", permission.getAuthority())
                    .isTrue();
        }
    }

    /**
     * The {@code Authority} constants are what {@code @PreAuthorize} sites reference. If one
     * existed that no enum constant used, an annotation could silently check a permission
     * nobody can ever hold — a permanent 403 that looks like a config problem.
     */
    @Test
    void everyAuthorityConstantBacksAnEnumConstant() throws IllegalAccessException {
        Set<String> declared = new HashSet<>();
        for (Field field : Permission.Authority.class.getDeclaredFields()) {
            declared.add((String) field.get(null));
        }

        Set<String> used = Arrays.stream(Permission.values())
                .map(Permission::getAuthority)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(declared).isEqualTo(used);
    }
}
