package com.ibrasoft.lensbridge.model.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void convenienceConstructorInitializesEmptyRoleSet() {
        User user = new User("Jane", "Doe", "1234567890", "jane@example.com", "hash");

        assertThat(user.getRoles()).isNotNull().isEmpty();
        assertThat(user.getFirstName()).isEqualTo("Jane");
        assertThat(user.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void addRoleAddsRoleToSet() {
        User user = new User();

        user.addRole(Role.ADMIN);

        assertThat(user.getRoles()).containsExactly(Role.ADMIN);
    }

    @Test
    void addRoleInitializesSetWhenNull() {
        User user = new User();
        user.setRoles(null);

        user.addRole(Role.USER);

        assertThat(user.getRoles()).containsExactly(Role.USER);
    }

    @Test
    void addRoleIsIdempotentForSameRole() {
        User user = new User();

        user.addRole(Role.USER);
        user.addRole(Role.USER);

        assertThat(user.getRoles()).containsExactly(Role.USER);
    }

    @Test
    void hasRoleReturnsTrueWhenRolePresent() {
        User user = new User();
        user.addRole(Role.ROOT);

        assertThat(user.hasRole(Role.ROOT)).isTrue();
    }

    @Test
    void hasRoleReturnsFalseWhenRoleAbsent() {
        User user = new User();
        user.addRole(Role.USER);

        assertThat(user.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void hasRoleReturnsFalseWhenRoleSetNull() {
        User user = new User();
        user.setRoles(null);

        assertThat(user.hasRole(Role.USER)).isFalse();
    }

    @Test
    void isVerifiedReturnsFalseWhenVerifiedAtNull() {
        User user = new User();

        assertThat(user.isVerified()).isFalse();
    }

    @Test
    void isVerifiedReturnsTrueWhenVerifiedAtSet() {
        User user = new User();
        user.setVerifiedAt(Instant.now());

        assertThat(user.isVerified()).isTrue();
    }

    @Test
    void getPasswordReturnsPasswordHash() {
        User user = new User();
        user.setPassword("secret-hash");

        assertThat(user.getPassword()).isEqualTo("secret-hash");
        assertThat(user.getPasswordHash()).isEqualTo("secret-hash");
    }
}
