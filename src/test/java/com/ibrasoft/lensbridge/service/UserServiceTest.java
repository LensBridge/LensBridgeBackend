package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.auth.request.ChangePasswordRequest;
import com.ibrasoft.lensbridge.dto.auth.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.auth.request.UpdateProfileRequest;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import com.ibrasoft.lensbridge.security.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
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
        req.setStudentNumber("1000001");
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

    @Test
    void createUserRejectsDuplicateStudentNumber() {
        SignupRequest req = signup();
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByStudentNumber("1000001")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyDirectlySetsVerifiedAt() {
        UUID id = UUID.randomUUID();
        User user = new User("A", "B", "1", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        User result = userService.verifyDirectly(id);

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getVerifiedAt()).isNotNull();
    }

    @Test
    void verifyDirectlyRejectsAlreadyVerifiedUser() {
        UUID id = UUID.randomUUID();
        User user = new User("A", "B", "1", "a@b.ca", "p");
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

    @Test
    void addRoleRejectsDuplicateRole() {
        UUID id = UUID.randomUUID();
        User user = new User("A", "B", "1", "a@b.ca", "p");
        user.addRole(Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.addRole(id, Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has role");
    }

    @Test
    void addRoleAddsNewRole() {
        UUID id = UUID.randomUUID();
        User user = new User("A", "B", "1", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        User result = userService.addRole(id, Role.ADMIN);

        assertThat(result.hasRole(Role.ADMIN)).isTrue();
    }

    @Test
    void removeRoleRejectsMissingRole() {
        UUID id = UUID.randomUUID();
        User user = new User("A", "B", "1", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.removeRole(id, Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not have role");
    }

    @Test
    void removeRoleRemovesExistingRole() {
        UUID id = UUID.randomUUID();
        User user = new User("A", "B", "1", "a@b.ca", "p");
        user.addRole(Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        User result = userService.removeRole(id, Role.ADMIN);

        assertThat(result.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void requestPasswordResetSendsEmailWhenUserExists() {
        User user = new User("A", "B", "1", "a@b.ca", "p");
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
        User user = new User("A", "B", "1", "a@b.ca", "old");
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
    void changePasswordRejectsWrongCurrentPassword() {
        User user = new User("A", "B", "1", "a@b.ca", "hash");
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
        User user = new User("A", "B", "1", "a@b.ca", "hash");
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
        User user = new User("A", "B", "1", "a@b.ca", "hash");
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
        User user = new User("Old", "Name", "1000001", "a@b.ca", "p");
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
        User user = new User("Old", "Name", "1000001", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("   ");

        User result = userService.updateProfile(id, req);

        assertThat(result.getFirstName()).isEqualTo("Old");
    }

    @Test
    void updateProfileRejectsTakenStudentNumber() {
        UUID id = UUID.randomUUID();
        User user = new User("Old", "Name", "1000001", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByStudentNumber("9999999")).thenReturn(true);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setStudentNumber("9999999");

        assertThatThrownBy(() -> userService.updateProfile(id, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void updateProfileAllowsSameStudentNumberWithoutUniquenessCheck() {
        UUID id = UUID.randomUUID();
        User user = new User("Old", "Name", "1000001", "a@b.ca", "p");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setStudentNumber("1000001");

        User result = userService.updateProfile(id, req);

        assertThat(result.getStudentNumber()).isEqualTo("1000001");
        verify(userRepository, never()).existsByStudentNumber(anyString());
    }
}
