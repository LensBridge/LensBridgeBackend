package com.ibrasoft.lensbridge.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ibrasoft.lensbridge.dto.auth.request.ChangePasswordRequest;
import com.ibrasoft.lensbridge.dto.auth.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.auth.request.UpdateProfileRequest;
import com.ibrasoft.lensbridge.dto.auth.response.UserInfoResponse;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
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

    @Value("${frontend.baseurl}")
    private String frontendBaseUrl;

    public Page<UserInfoResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(u ->
                new UserInfoResponse(u.getId(), u.getFirstName(), u.getLastName(),
                        u.getEmail(), u.getStudentNumber(), u.isVerified(), u.getRoles()));
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

    public User addRole(UUID userId, Role role) {
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.hasRole(role)) {
            throw new IllegalArgumentException("User already has role: " + role);
        }
        user.addRole(role);
        return saveUser(user);
    }

    public User removeRole(UUID userId, Role role) {
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.hasRole(role)) {
            throw new IllegalArgumentException("User does not have role: " + role);
        }
        user.getRoles().remove(role);
        return saveUser(user);
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
