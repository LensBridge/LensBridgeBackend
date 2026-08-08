package com.ibrasoft.lensbridge.security.services;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorityResolverTest {

    private UserRepository userRepository;
    private AuthorityResolver resolver;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        resolver = new AuthorityResolver(userRepository);
        when(userRepository.findDirectPermissions(any())).thenReturn(Set.of());
    }

    private User user(Role... roles) {
        User user = new User("A", "B", "1", "a@b.ca", "p");
        user.setId(UUID.randomUUID());
        for (Role role : roles) {
            user.addRole(role);
        }
        return user;
    }

    private Set<String> authorityStrings(User user) {
        return resolver.resolveAuthorities(user).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    void rolesAreExpandedIntoTheirPermissions() {
        assertThat(resolver.resolvePermissions(user(Role.BOARD_EDITOR)))
                .containsExactlyInAnyOrderElementsOf(Role.BOARD_EDITOR.getPermissions());
    }

    /**
     * Both forms are emitted so the untouched legacy media endpoints, which still say
     * hasRole('ADMIN'), keep working while board endpoints check permissions.
     */
    @Test
    void authoritiesCarryBothTheRoleAndItsPermissions() {
        Set<String> authorities = authorityStrings(user(Role.ADMIN));

        assertThat(authorities).contains("ROLE_ADMIN");
        assertThat(authorities).contains(Permission.MEDIA_UPLOAD_MODERATE.getAuthority());
    }

    @Test
    void directGrantsAddToWhatRolesConfer() {
        User user = user(Role.BOARD_EDITOR);
        when(userRepository.findDirectPermissions(user.getId()))
                .thenReturn(Set.of(Permission.BOARD_COMMAND_INSPECT));

        Set<Permission> resolved = resolver.resolvePermissions(user);

        assertThat(resolved)
                .containsAll(Role.BOARD_EDITOR.getPermissions())
                .contains(Permission.BOARD_COMMAND_INSPECT);
    }

    @Test
    void aUserWithNoRolesGetsNothing() {
        assertThat(resolver.resolvePermissions(user())).isEmpty();
    }

    /** Unsaved entities appear in tests and in the signup path; they must not blow up. */
    @Test
    void aUserWithNoIdResolvesWithoutHittingTheRepository() {
        User unsaved = new User("A", "B", "1", "a@b.ca", "p");
        unsaved.addRole(Role.USER);

        assertThat(resolver.resolvePermissions(unsaved))
                .containsExactly(Permission.MEDIA_UPLOAD_SELF);
    }

    @Test
    void rootResolvesToEveryPermission() {
        assertThat(resolver.resolvePermissions(user(Role.ROOT)))
                .containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void resolvingByIdReturnsEmptyForAnUnknownUser() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(java.util.Optional.empty());

        assertThat(resolver.resolvePermissions(unknown)).isEmpty();
    }
}
