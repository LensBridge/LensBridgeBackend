package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.auth.request.ChangePasswordRequest;
import com.ibrasoft.lensbridge.dto.auth.request.CreateUserRequest;
import com.ibrasoft.lensbridge.dto.auth.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.auth.request.UpdateProfileRequest;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import com.ibrasoft.lensbridge.security.services.AuthorityResolver;
import com.ibrasoft.lensbridge.security.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private VerificationTokenService verificationTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuthorityResolver authorityResolver;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void echoSave() {
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SignupRequest signup() {
        SignupRequest req = new SignupRequest();
        req.setFirstName("Jane");
        req.setLastName("Doe");
        req.setEmail("jane@mail.utoronto.ca");
        req.setPassword("password1");
        return req;
    }

    @Test
    void createUserEncodesPasswordAndAssignsUserRole() {
        SignupRequest req = signup();
        when(passwordEncoder.encode("password1")).thenReturn("ENC");

        User created = userService.createUser(req, false);

        assertThat(created.getPassword()).isEqualTo("ENC");
        assertThat(created.getRoles()).containsExactly(Role.USER);
        assertThat(created.getEmail()).isEqualTo("jane@mail.utoronto.ca");
    }

    @Test
    void createUserWithNullPasswordStoresNullWithoutEncoding() {
        SignupRequest req = signup();
        req.setPassword(null);

        User created = userService.createUser(req, false);

        assertThat(created.getPassword()).isNull();
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void createUserSendsVerificationEmailWhenRequested() {
        SignupRequest req = signup();
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(verificationTokenService.generateEmailVerificationToken(any(User.class))).thenReturn("tok123");

        userService.createUser(req, true);

        verify(verificationTokenService).generateEmailVerificationToken(any(User.class));
        verify(emailService).sendVerificationEmail(eqEmail(), any(), org.mockito.ArgumentMatchers.contains("token=tok123"));
    }

    private static String eqEmail() {
        return org.mockito.ArgumentMatchers.eq("jane@mail.utoronto.ca");
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        SignupRequest req = signup();
        when(userRepository.existsByEmail("jane@mail.utoronto.ca")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
    }

    private CreateUserRequest adminCreate() {
        CreateUserRequest req = new CreateUserRequest();
        req.setFirstName("Jane");
        req.setLastName("Doe");
        req.setEmail("jane@mail.utoronto.ca");
        return req;
    }

    @Test
    void createUserByAdminWithoutPasswordLeavesAccountDisabledAndEmailsResetLink() {
        when(passwordEncoder.encode(anyString())).thenReturn("UNUSABLE");
        when(verificationTokenService.generatePasswordResetToken(any(User.class))).thenReturn("rtok");

        User created = userService.createUserByAdmin(adminCreate());

        assertThat(created.isVerified()).isFalse();
        assertThat(created.getRoles()).containsExactly(Role.USER);
        verify(emailService).sendPasswordResetEmail(eqEmail(), any(),
                org.mockito.ArgumentMatchers.contains("token=rtok"));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void createUserByAdminWithoutPasswordStoresAHashNoInputCanMatch() {
        ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
        when(passwordEncoder.encode(encoded.capture())).thenReturn("UNUSABLE");

        User created = userService.createUserByAdmin(adminCreate());

        // Never null: passwordHash is @NotBlank and nullable = false.
        assertThat(created.getPassword()).isEqualTo("UNUSABLE");
        assertThat(encoded.getValue()).hasSize(64); // 32 random bytes, hex
    }

    @Test
    void createUserByAdminWithPasswordEnablesAccountAndSendsNothing() {
        CreateUserRequest req = adminCreate();
        req.setPassword("password1");
        when(passwordEncoder.encode("password1")).thenReturn("ENC");

        User created = userService.createUserByAdmin(req);

        assertThat(created.getPassword()).isEqualTo("ENC");
        assertThat(created.isVerified()).isTrue();
        verifyNoInteractions(emailService);
        verifyNoInteractions(verificationTokenService);
    }

    @Test
    void createUserByAdminTreatsBlankPasswordAsAbsent() {
        CreateUserRequest req = adminCreate();
        req.setPassword("   ");
        when(passwordEncoder.encode(anyString())).thenReturn("UNUSABLE");

        User created = userService.createUserByAdmin(req);

        assertThat(created.isVerified()).isFalse();
        verify(emailService).sendPasswordResetEmail(eqEmail(), any(), anyString());
    }

    @Test
    void createUserByAdminRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("jane@mail.utoronto.ca")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUserByAdmin(adminCreate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void verifyDirectlySetsVerifiedAt() {
        UUID id = UUID.randomUUID();
        User user = new User("A", "B", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        User result = userService.verifyDirectly(id);

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getVerifiedAt()).isNotNull();
    }

    @Test
    void verifyDirectlyRejectsAlreadyVerifiedUser() {
        UUID id = UUID.randomUUID();
        User user = new User("A", "B", "a@b.ca", "p");
        user.setVerifiedAt(java.time.Instant.now());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.verifyDirectly(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already verified");
    }

    @Test
    void verifyDirectlyRejectsUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifyDirectly(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ==================== Grants ====================

    private static final String ROOT_EMAIL = "root@mail.utoronto.ca";

    /** An acting user holding everything, and another id to keep IAM_ROLE_GRANT alive. */
    private User rootActor() {
        User root = new User("Root", "User", ROOT_EMAIL, "p");
        root.setId(UUID.randomUUID());
        root.addRole(Role.ROOT);
        lenient().when(userRepository.findByEmail(ROOT_EMAIL)).thenReturn(Optional.of(root));
        lenient().when(authorityResolver.resolvePermissions(root))
                .thenReturn(EnumSet.allOf(Permission.class));
        return root;
    }

    private User target(UUID id) {
        User user = new User("A", "B", "a@b.ca", "p");
        user.setId(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void addRoleRejectsDuplicateRole() {
        rootActor();
        UUID id = UUID.randomUUID();
        target(id).addRole(Role.BOARD_ADMIN);

        assertThatThrownBy(() -> userService.addRole(ROOT_EMAIL, id, Role.BOARD_ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has role");
    }

    @Test
    void addRoleAddsNewRole() {
        rootActor();
        UUID id = UUID.randomUUID();
        target(id);

        User result = userService.addRole(ROOT_EMAIL, id, Role.BOARD_ADMIN);

        assertThat(result.hasRole(Role.BOARD_ADMIN)).isTrue();
    }

    @Test
    void removeRoleRejectsMissingRole() {
        rootActor();
        UUID id = UUID.randomUUID();
        target(id);

        assertThatThrownBy(() -> userService.removeRole(ROOT_EMAIL, id, Role.BOARD_ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not have role");
    }

    @Test
    void removeRoleRemovesExistingRole() {
        rootActor();
        UUID id = UUID.randomUUID();
        target(id).addRole(Role.BOARD_ADMIN);

        User result = userService.removeRole(ROOT_EMAIL, id, Role.BOARD_ADMIN);

        assertThat(result.hasRole(Role.BOARD_ADMIN)).isFalse();
    }

    @Test
    void addRoleRejectsSelfModification() {
        User root = rootActor();
        // The target lookup happens first, and when you target yourself it succeeds.
        when(userRepository.findById(root.getId())).thenReturn(Optional.of(root));

        assertThatThrownBy(() -> userService.addRole(ROOT_EMAIL, root.getId(), Role.BOARD_ADMIN))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("your own roles");
    }

    @Test
    void addRoleRejectsGrantingPermissionsTheActorDoesNotHold() {
        User editor = new User("Ed", "Itor", "editor@mail.utoronto.ca", "p");
        editor.setId(UUID.randomUUID());
        editor.addRole(Role.BOARD_EDITOR);
        when(userRepository.findByEmail(editor.getEmail())).thenReturn(Optional.of(editor));
        when(authorityResolver.resolvePermissions(editor))
                .thenReturn(EnumSet.copyOf(Role.BOARD_EDITOR.getPermissions()));

        UUID id = UUID.randomUUID();
        target(id);

        // BOARD_ADMIN confers device enrollment and screenshots; a plain editor holds neither.
        assertThatThrownBy(() -> userService.addRole(editor.getEmail(), id, Role.BOARD_ADMIN))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("board:device:enroll");
    }

    @Test
    void addRoleAllowsGrantingASubsetOfWhatTheActorHolds() {
        User boardAdmin = new User("B", "A", "boardadmin@mail.utoronto.ca", "p");
        boardAdmin.setId(UUID.randomUUID());
        boardAdmin.addRole(Role.BOARD_ADMIN);
        when(userRepository.findByEmail(boardAdmin.getEmail())).thenReturn(Optional.of(boardAdmin));
        when(authorityResolver.resolvePermissions(boardAdmin))
                .thenReturn(EnumSet.copyOf(Role.BOARD_ADMIN.getPermissions()));

        UUID id = UUID.randomUUID();
        target(id);

        User result = userService.addRole(boardAdmin.getEmail(), id, Role.BOARD_EDITOR);

        assertThat(result.hasRole(Role.BOARD_EDITOR)).isTrue();
    }

    @Test
    void removeRoleRefusesToStripTheLastGrantHolder() {
        rootActor();
        UUID id = UUID.randomUUID();
        target(id).addRole(Role.ROOT);

        // Only this user holds a role conferring iam:role:grant.
        when(userRepository.findIdsByRoleIn(any())).thenReturn(new HashSet<>(Set.of(id)));
        when(userRepository.findIdsByDirectPermission(Permission.IAM_ROLE_GRANT))
                .thenReturn(new HashSet<>());

        assertThatThrownBy(() -> userService.removeRole(ROOT_EMAIL, id, Role.ROOT))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("last user holding it");
    }

    @Test
    void removeRoleAllowsStrippingRootWhenAnotherHolderRemains() {
        User root = rootActor();
        UUID id = UUID.randomUUID();
        target(id).addRole(Role.ROOT);

        when(userRepository.findIdsByRoleIn(any()))
                .thenReturn(new HashSet<>(Set.of(id, root.getId())));
        when(userRepository.findIdsByDirectPermission(Permission.IAM_ROLE_GRANT))
                .thenReturn(new HashSet<>());

        User result = userService.removeRole(ROOT_EMAIL, id, Role.ROOT);

        assertThat(result.hasRole(Role.ROOT)).isFalse();
    }

    /**
     * Demotion is an escalation path. Someone with a direct iam:role:grant must not be able
     * to strip ROLE_ROOT off every root account and end as the only administrator left —
     * the last-holder guard never fires, because their own direct grant keeps the set
     * non-empty.
     */
    @Test
    void removeRoleRejectsStrippingPermissionsTheActorDoesNotHold() {
        User grantOnly = new User("G", "Only", "grantonly@mail.utoronto.ca", "p");
        grantOnly.setId(UUID.randomUUID());
        grantOnly.addDirectPermission(Permission.IAM_ROLE_GRANT);
        when(userRepository.findByEmail(grantOnly.getEmail())).thenReturn(Optional.of(grantOnly));
        when(authorityResolver.resolvePermissions(grantOnly))
                .thenReturn(EnumSet.of(Permission.IAM_ROLE_GRANT));

        UUID victim = UUID.randomUUID();
        target(victim).addRole(Role.ROOT);

        assertThatThrownBy(() -> userService.removeRole(grantOnly.getEmail(), victim, Role.ROOT))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("cannot remove");
    }

    @Test
    void revokePermissionRejectsRevokingWhatTheActorDoesNotHold() {
        User editor = new User("Ed", "Itor", "ed@mail.utoronto.ca", "p");
        editor.setId(UUID.randomUUID());
        editor.addRole(Role.BOARD_EDITOR);
        editor.addDirectPermission(Permission.IAM_ROLE_GRANT);
        when(userRepository.findByEmail(editor.getEmail())).thenReturn(Optional.of(editor));
        Set<Permission> held = EnumSet.copyOf(Role.BOARD_EDITOR.getPermissions());
        held.add(Permission.IAM_ROLE_GRANT);
        when(authorityResolver.resolvePermissions(editor)).thenReturn(held);

        UUID victim = UUID.randomUUID();
        target(victim).addDirectPermission(Permission.BOARD_COMMAND_INSPECT);

        assertThatThrownBy(() -> userService.revokePermission(
                editor.getEmail(), victim, Permission.BOARD_COMMAND_INSPECT))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("board:command:inspect");
    }

    /**
     * The lockout guard must look at what the target would actually be left holding. A ROOT
     * who also carries a direct iam:role:grant keeps it after ROLE_ROOT comes off, so that
     * removal is nobody's lockout and must not be refused.
     */
    @Test
    void removeRoleIsAllowedWhenTheTargetKeepsGrantByAnotherPath() {
        rootActor();
        UUID id = UUID.randomUUID();
        User victim = target(id);
        victim.addRole(Role.ROOT);
        when(userRepository.findDirectPermissions(id))
                .thenReturn(Set.of(Permission.IAM_ROLE_GRANT));

        User result = userService.removeRole(ROOT_EMAIL, id, Role.ROOT);

        assertThat(result.hasRole(Role.ROOT)).isFalse();
        // No holder counting at all: the target keeps the capability, so there is no
        // lockout question to ask. Deliberately left unstubbed to assert that.
        verify(userRepository, never()).findIdsByRoleIn(any());
        verify(userRepository, never()).findIdsByDirectPermission(any());
    }

    @Test
    void grantPermissionAddsADirectGrant() {
        rootActor();
        UUID id = UUID.randomUUID();
        target(id);

        User result = userService.grantPermission(ROOT_EMAIL, id, Permission.BOARD_COMMAND_INSPECT);

        assertThat(result.hasDirectPermission(Permission.BOARD_COMMAND_INSPECT)).isTrue();
    }

    @Test
    void revokePermissionRejectsAPermissionThatCameFromARole() {
        rootActor();
        UUID id = UUID.randomUUID();
        target(id).addRole(Role.BOARD_EDITOR);

        assertThatThrownBy(() ->
                userService.revokePermission(ROOT_EMAIL, id, Permission.BOARD_POSTER_WRITE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("removing the role");
    }

    @Test
    void requestPasswordResetSendsEmailWhenUserExists() {
        User user = new User("A", "B", "a@b.ca", "p");
        when(userRepository.findByEmail("a@b.ca")).thenReturn(Optional.of(user));
        when(verificationTokenService.generatePasswordResetToken(user)).thenReturn("rtok");

        userService.requestPasswordReset("a@b.ca");

        verify(emailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq("a@b.ca"),
                any(), org.mockito.ArgumentMatchers.contains("token=rtok"));
    }

    @Test
    void requestPasswordResetIsNoOpWhenUserMissing() {
        when(userRepository.findByEmail("missing@b.ca")).thenReturn(Optional.empty());

        userService.requestPasswordReset("missing@b.ca");

        verifyNoInteractions(emailService);
        verifyNoInteractions(verificationTokenService);
    }

    @Test
    void resetPasswordEncodesNewPasswordAndRevokesTokens() {
        User user = new User("A", "B", "a@b.ca", "old");
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        VerificationToken vt = VerificationToken.builder().user(user).build();
        when(verificationTokenService.consumePasswordReset("ptok")).thenReturn(vt);
        when(passwordEncoder.encode("newpass")).thenReturn("ENC");

        User result = userService.resetPassword("ptok", "newpass");

        assertThat(result.getPassword()).isEqualTo("ENC");
        verify(refreshTokenService).revokeAllUserTokens(userId);
    }

    @Test
    void resetPasswordEnablesAStillUnverifiedAccount() {
        // The admin-invite path: without this the invitee sets a password and is still disabled.
        User user = new User("A", "B", "a@b.ca", "unusable");
        user.setId(UUID.randomUUID());
        VerificationToken vt = VerificationToken.builder().user(user).build();
        when(verificationTokenService.consumePasswordReset("ptok")).thenReturn(vt);
        when(passwordEncoder.encode("newpass")).thenReturn("ENC");

        User result = userService.resetPassword("ptok", "newpass");

        assertThat(result.isVerified()).isTrue();
    }

    @Test
    void resetPasswordLeavesAnExistingVerificationTimestampAlone() {
        User user = new User("A", "B", "a@b.ca", "old");
        user.setId(UUID.randomUUID());
        Instant verifiedAt = Instant.now().minusSeconds(86400);
        user.setVerifiedAt(verifiedAt);
        VerificationToken vt = VerificationToken.builder().user(user).build();
        when(verificationTokenService.consumePasswordReset("ptok")).thenReturn(vt);
        when(passwordEncoder.encode("newpass")).thenReturn("ENC");

        User result = userService.resetPassword("ptok", "newpass");

        assertThat(result.getVerifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        User user = new User("A", "B", "a@b.ca", "hash");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrong");
        req.setNewPassword("brandnew");
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(user, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePasswordRejectsShortNewPassword() {
        User user = new User("A", "B", "a@b.ca", "hash");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("right");
        req.setNewPassword("12345");
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(user, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 6 characters");
    }

    @Test
    void changePasswordUpdatesHashOnSuccess() {
        User user = new User("A", "B","a@b.ca", "hash");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("right");
        req.setNewPassword("longenough");
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);
        when(passwordEncoder.encode("longenough")).thenReturn("ENC");

        User result = userService.changePassword(user, req);

        assertThat(result.getPassword()).isEqualTo("ENC");
    }

    @Test
    void updateProfileTrimsAndUpdatesFields() {
        UUID id = UUID.randomUUID();
        User user = new User("Old", "Name", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("  New  ");
        req.setLastName("  Last  ");

        User result = userService.updateProfile(id, req);

        assertThat(result.getFirstName()).isEqualTo("New");
        assertThat(result.getLastName()).isEqualTo("Last");
    }

    @Test
    void updateProfileIgnoresBlankFields() {
        UUID id = UUID.randomUUID();
        User user = new User("Old", "Name", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("   ");

        User result = userService.updateProfile(id, req);

        assertThat(result.getFirstName()).isEqualTo("Old");
    }
}
