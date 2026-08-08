package com.ibrasoft.lensbridge.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ibrasoft.lensbridge.dto.auth.request.ChangePasswordRequest;
import com.ibrasoft.lensbridge.dto.auth.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.auth.request.UpdateProfileRequest;
import com.ibrasoft.lensbridge.dto.auth.response.UserInfoResponse;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import com.ibrasoft.lensbridge.security.services.AuthorityResolver;
import com.ibrasoft.lensbridge.security.services.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationTokenService verificationTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthorityResolver authorityResolver;

    @Value("${frontend.baseurl}")
    private String frontendBaseUrl;

    public Page<UserInfoResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(u -> {
            // Fetched once and used for both lists. Going through AuthorityResolver here
            // would re-query the same direct grants, making a 50-row page 101 queries.
            Set<Permission> direct = userRepository.findDirectPermissions(u.getId());

            Set<Permission> effective = EnumSet.noneOf(Permission.class);
            for (Role role : u.getRoles()) {
                effective.addAll(role.getPermissions());
            }
            effective.addAll(direct);

            return new UserInfoResponse(
                    u.getId(), u.getFirstName(), u.getLastName(),
                    u.getEmail(), u.getStudentNumber(), u.isVerified(), u.getRoles(),
                    authorities(direct), authorities(effective));
        });
    }

    private static List<String> authorities(Set<Permission> permissions) {
        return permissions.stream().map(Permission::getAuthority).sorted().toList();
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByStudentNumber(String studentNumber) {
        return userRepository.existsByStudentNumber(studentNumber);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public User createUser(SignupRequest signUpRequest, boolean sendConfirmEmail) {
        log.info("Creating new user: {}", signUpRequest.getEmail());

        if (existsByEmail(signUpRequest.getEmail()) || existsByStudentNumber(signUpRequest.getStudentNumber())) {
            throw new IllegalArgumentException("User with this email or student number already exists");
        }

        User user = new User(
                signUpRequest.getFirstName(),
                signUpRequest.getLastName(),
                signUpRequest.getStudentNumber(),
                signUpRequest.getEmail(),
                signUpRequest.getPassword() == null ? null : passwordEncoder.encode(signUpRequest.getPassword())
        );
        user.setRoles(new HashSet<>());
        user.addRole(Role.USER);

        User savedUser = saveUser(user);

        if (sendConfirmEmail) {
            String token = verificationTokenService.generateEmailVerificationToken(savedUser);
            String verificationUrl = frontendBaseUrl + "/verify-email?token=" + token;
            emailService.sendVerificationEmail(savedUser.getEmail(),
                    savedUser.getFirstName(), verificationUrl);
        }

        log.info("User created: {}", user.getEmail());
        return savedUser;
    }

    public User createUser(SignupRequest signUpRequest) {
        return createUser(signUpRequest, true);
    }

    public User verifyUserEmail(String plaintextToken) {
        return verificationTokenService.consumeEmailVerification(plaintextToken);
    }

    public User verifyDirectly(UUID userId) {
        log.info("Directly verifying user: {}", userId);
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isVerified()) {
            throw new IllegalArgumentException("User is already verified");
        }

        user.setVerifiedAt(Instant.now());
        User saved = saveUser(user);
        log.info("User directly verified: {}", user.getEmail());
        return saved;
    }

    // ==================== Grants ====================
    //
    // Three rules apply to every change below, and they are the difference between a
    // permission system and a suggestion:
    //
    //   1. Nobody edits their own grants. Otherwise iam:role:grant is self-service ROOT.
    //   2. Nobody hands out what they do not hold. Otherwise iam:role:grant is ROOT anyway,
    //      one step removed.
    //   3. Nobody takes away what they do not hold either. Rule 2 alone is not enough:
    //      demotion is an escalation path. Someone holding a direct iam:role:grant could
    //      otherwise strip ROLE_ROOT from every root account one at a time — rule 4 never
    //      fires, because their own direct grant keeps the holder set non-empty — and end
    //      as the only administrator left standing.
    //   4. The last holder of iam:role:grant cannot be stripped of it, or the org locks
    //      itself out of its own admin console with no way back in short of a DB edit.

    public User addRole(String actingEmail, UUID userId, Role role) {
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        User acting = requireActingUser(actingEmail);

        denySelfModification(acting, userId, "roles");
        denyEscalation(acting, role.getPermissions(), "role " + role);

        if (user.hasRole(role)) {
            throw new IllegalArgumentException("User already has role: " + role);
        }
        user.addRole(role);
        log.info("{} granted role {} to {}", actingEmail, role, user.getEmail());
        return saveUser(user);
    }

    public User removeRole(String actingEmail, UUID userId, Role role) {
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        User acting = requireActingUser(actingEmail);

        denySelfModification(acting, userId, "roles");
        denyDeprivation(acting, role.getPermissions(), "role " + role);

        if (!user.hasRole(role)) {
            throw new IllegalArgumentException("User does not have role: " + role);
        }
        denyLastGrantHolderRemovalIfLosingIt(user, withoutRole(user, role));
        user.getRoles().remove(role);
        log.info("{} removed role {} from {}", actingEmail, role, user.getEmail());
        return saveUser(user);
    }

    /**
     * Grants a single permission on top of whatever the user's roles already confer.
     * <p>
     * {@code @Transactional} is load-bearing: {@code User.directPermissions} is LAZY, and
     * without an enclosing transaction {@code findById} hands back a detached entity whose
     * collection throws on first touch. {@code open-in-view} is false in prod and true in
     * dev, so the failure would appear only in production.
     */
    @Transactional
    public User grantPermission(String actingEmail, UUID userId, Permission permission) {
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        User acting = requireActingUser(actingEmail);

        denySelfModification(acting, userId, "permissions");
        denyEscalation(acting, Set.of(permission), permission.getAuthority());

        if (user.hasDirectPermission(permission)) {
            throw new IllegalArgumentException("User already has permission: " + permission.getAuthority());
        }
        user.addDirectPermission(permission);
        log.info("{} granted permission {} to {}", actingEmail, permission.getAuthority(), user.getEmail());
        return saveUser(user);
    }

    /** See {@link #grantPermission} on why this is transactional. */
    @Transactional
    public User revokePermission(String actingEmail, UUID userId, Permission permission) {
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        User acting = requireActingUser(actingEmail);

        denySelfModification(acting, userId, "permissions");
        denyDeprivation(acting, Set.of(permission), permission.getAuthority());

        if (!user.hasDirectPermission(permission)) {
            throw new IllegalArgumentException(
                    "User does not have a direct grant of: " + permission.getAuthority()
                    + " (a permission conferred by a role is removed by removing the role)");
        }
        denyLastGrantHolderRemovalIfLosingIt(user, withoutDirectPermission(user, permission));
        user.getDirectPermissions().remove(permission);
        log.info("{} revoked permission {} from {}", actingEmail, permission.getAuthority(), user.getEmail());
        return saveUser(user);
    }

    private User requireActingUser(String actingEmail) {
        return findByEmail(actingEmail)
                .orElseThrow(() -> new SecurityException("Acting user not found: " + actingEmail));
    }

    private void denySelfModification(User acting, UUID targetUserId, String what) {
        if (Objects.equals(acting.getId(), targetUserId)) {
            throw new SecurityException("You cannot change your own " + what);
        }
    }

    private void denyEscalation(User acting, Set<Permission> requested, String label) {
        Set<Permission> held = authorityResolver.resolvePermissions(acting);
        Set<Permission> missing = new HashSet<>(requested);
        missing.removeAll(held);
        if (!missing.isEmpty()) {
            throw new SecurityException("You cannot grant " + label
                    + " because you do not hold: " + missing.stream()
                            .map(Permission::getAuthority).sorted().toList());
        }
    }

    /**
     * The mirror of {@link #denyEscalation}. Taking a capability away is as much a route to
     * sole control as handing one out, so removals are held to the same bar.
     */
    private void denyDeprivation(User acting, Set<Permission> requested, String label) {
        Set<Permission> held = authorityResolver.resolvePermissions(acting);
        Set<Permission> missing = new HashSet<>(requested);
        missing.removeAll(held);
        if (!missing.isEmpty()) {
            throw new SecurityException("You cannot remove " + label
                    + " because you do not hold: " + missing.stream()
                            .map(Permission::getAuthority).sorted().toList());
        }
    }

    /**
     * Refuses to leave nobody able to grant roles.
     * <p>
     * Only fires when the target would actually lose {@code iam:role:grant}: a ROOT who also
     * carries it as a direct grant keeps it after {@code ROLE_ROOT} is removed, and that
     * removal is nobody's lockout. Holders are counted by role bundle and by direct grant,
     * so this stays correct if the permission is ever added to a role other than ROOT.
     *
     * @param remaining the target's permissions as they would be after the removal
     */
    private void denyLastGrantHolderRemovalIfLosingIt(User target, Set<Permission> remaining) {
        if (remaining.contains(Permission.IAM_ROLE_GRANT)) {
            return; // keeps the capability by another path
        }
        if (!effectivePermissions(target).contains(Permission.IAM_ROLE_GRANT)) {
            return; // never had it
        }

        Set<Role> grantingRoles = Arrays.stream(Role.values())
                .filter(r -> r.getPermissions().contains(Permission.IAM_ROLE_GRANT))
                .collect(Collectors.toSet());

        Set<UUID> holders = new HashSet<>();
        if (!grantingRoles.isEmpty()) {
            holders.addAll(userRepository.findIdsByRoleIn(grantingRoles));
        }
        holders.addAll(userRepository.findIdsByDirectPermission(Permission.IAM_ROLE_GRANT));
        holders.remove(target.getId());

        if (holders.isEmpty()) {
            throw new SecurityException("Refusing to remove " + Permission.IAM_ROLE_GRANT.getAuthority()
                    + " from the last user holding it — grant it to someone else first");
        }
    }

    /**
     * Roles expanded plus direct grants. Computed here rather than through
     * {@code AuthorityResolver} so the "held before" and "held after" sides of the lockout
     * check are derived the same way and cannot disagree.
     */
    private Set<Permission> effectivePermissions(User target) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (Role role : target.getRoles()) {
            permissions.addAll(role.getPermissions());
        }
        permissions.addAll(directPermissionsOf(target));
        return permissions;
    }

    /** The target's effective permissions with {@code role} taken away, without mutating them. */
    private Set<Permission> withoutRole(User target, Role role) {
        Set<Permission> remaining = EnumSet.noneOf(Permission.class);
        for (Role kept : target.getRoles()) {
            if (kept != role) remaining.addAll(kept.getPermissions());
        }
        remaining.addAll(directPermissionsOf(target));
        return remaining;
    }

    /** The target's effective permissions with a direct grant taken away. */
    private Set<Permission> withoutDirectPermission(User target, Permission permission) {
        Set<Permission> remaining = EnumSet.noneOf(Permission.class);
        for (Role kept : target.getRoles()) {
            remaining.addAll(kept.getPermissions());
        }
        for (Permission direct : directPermissionsOf(target)) {
            if (direct != permission) remaining.add(direct);
        }
        return remaining;
    }

    private Set<Permission> directPermissionsOf(User target) {
        if (target.getId() == null) return Set.of(); // unsaved entity
        Set<Permission> direct = userRepository.findDirectPermissions(target.getId());
        return direct == null ? Set.of() : direct;
    }

    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = verificationTokenService.generatePasswordResetToken(user);
            String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), resetUrl);
        });
    }

    public boolean validateResetToken(String token) {
        return verificationTokenService.isValidResetToken(token);
    }

    public User resetPassword(String token, String newPassword) {
        VerificationToken verificationToken = verificationTokenService.consumePasswordReset(token);
        User user = verificationToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        User saved = saveUser(user);
        refreshTokenService.revokeAllUserTokens(user.getId());
        return saved;
    }

    public User changePassword(User user, ChangePasswordRequest changePasswordRequest) {
        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (changePasswordRequest.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long");
        }
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        return saveUser(user);
    }

    public User updateProfile(UUID userId, UpdateProfileRequest updateRequest) {
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (updateRequest.getFirstName() != null && !updateRequest.getFirstName().isBlank()) {
            user.setFirstName(updateRequest.getFirstName().trim());
        }
        if (updateRequest.getLastName() != null && !updateRequest.getLastName().isBlank()) {
            user.setLastName(updateRequest.getLastName().trim());
        }
        if (updateRequest.getStudentNumber() != null && !updateRequest.getStudentNumber().isBlank()) {
            String newNum = updateRequest.getStudentNumber().trim();
            if (!newNum.equals(user.getStudentNumber()) && existsByStudentNumber(newNum)) {
                throw new IllegalArgumentException("Student number is already taken");
            }
            user.setStudentNumber(newNum);
        }

        return saveUser(user);
    }
}
