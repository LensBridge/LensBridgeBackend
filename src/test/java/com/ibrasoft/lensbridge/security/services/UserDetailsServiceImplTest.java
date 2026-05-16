package com.ibrasoft.lensbridge.security.services;

import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl service;

    private User newUser(boolean verified) {
        User user = new User("Jane", "Doe", "1234567890", "jane@example.com", "secret");
        user.setVerified(verified);
        // Constructor seeds an immutable List.of(); replace with a mutable list
        // before adding roles (User.addRole only guards against null, not immutability).
        user.setRoles(new ArrayList<>(List.of(Role.ROLE_USER.getAuthority())));
        return user;
    }

    @Test
    void loadUserByUsernameReturnsUserDetailsForKnownEmail() {
        User user = newUser(true);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("jane@example.com");

        assertThat(details.getUsername()).isEqualTo("jane@example.com");
        assertThat(details.getPassword()).isEqualTo("secret");
        assertThat(details.getAuthorities()).extracting("authority")
                .contains(Role.ROLE_USER.getAuthority());
    }

    @Test
    void loadUserByUsernameThrowsWhenEmailUnknown() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }

    /**
     * Documents ACTUAL (likely buggy) behavior: the SQL-migration version of
     * UserDetailsServiceImpl returns UserDetailsImpl.build(user), and
     * UserDetailsImpl does NOT override UserDetails#isEnabled(). In Spring
     * Security 6 that default method returns true, so an unverified user is
     * still reported as enabled. The pre-migration implementation disabled
     * unverified users via .disabled(!user.isVerified()). Flagged in PR.
     */
    @Test
    void loadUserByUsernameDoesNotDisableUnverifiedUser_actualBehavior() {
        User user = newUser(false);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("jane@example.com");

        assertThat(details.isEnabled())
                .as("UserDetailsImpl does not override isEnabled(); unverified user remains enabled (likely bug)")
                .isTrue();
    }

    @Test
    void loadUserByUsernameExposesVerificationStateOnPrincipal() {
        User unverified = newUser(false);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(unverified));

        UserDetails details = service.loadUserByUsername("jane@example.com");

        assertThat(details).isInstanceOf(UserDetailsImpl.class);
        assertThat(((UserDetailsImpl) details).isVerified()).isFalse();
    }

    @Test
    void loadUserByUsernameVerifiedUserIsEnabled() {
        User user = newUser(true);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("jane@example.com");

        assertThat(details.isEnabled()).isTrue();
        assertThat(((UserDetailsImpl) details).isVerified()).isTrue();
    }
}
