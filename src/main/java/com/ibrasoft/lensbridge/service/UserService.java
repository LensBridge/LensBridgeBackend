package com.ibrasoft.lensbridge.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ibrasoft.lensbridge.dto.request.ChangePasswordRequest;
import com.ibrasoft.lensbridge.dto.request.SignupRequest;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.UserRepository;
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
    
    @Value("${frontend.baseurl}")
    private String frontendBaseUrl;

    /**
     * Get all users with pagination
     */
    public Page<User> getAllUsers(Pageable pageable) {
        log.info("Fetching all users with pagination: {}", pageable);
        return userRepository.findAll(pageable);
    }
    
    /**
     * Find user by ID
     */
    public Optional<User> findById(UUID id) {
        log.debug("Finding user by ID: {}", id);
        return userRepository.findById(id);
    }
    
    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        return userRepository.findByEmail(email);
    }
    
    /**
     * Find user by verification token
     */
    public Optional<User> findByVerificationToken(String token) {
        log.debug("Finding user by verification token");
        return userRepository.findByVerificationToken(token);
    }
    
    /**
     * Check if email exists
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
    
    /**
     * Check if student number exists
     */
    public boolean existsByStudentNumber(String studentNumber) {
        return userRepository.existsByStudentNumber(studentNumber);
    }
    
    /**
     * Save user
     */
    public User saveUser(User user) {
        log.debug("Saving user: {}", user.getEmail());
        return userRepository.save(user);
    }
    
    /**
     * Create a new user from signup request
     */
    public User createUser(SignupRequest signUpRequest, boolean sendConfirmEmail) throws Exception {
        log.info("Creating new user with email: {}", signUpRequest.getEmail());
        
        // Check if user already exists
        if (existsByEmail(signUpRequest.getEmail()) || existsByStudentNumber(signUpRequest.getStudentNumber())) {
            throw new IllegalArgumentException("User with this email or student number already exists");
        }
        
        // Create new user
        User user = new User(
            signUpRequest.getFirstName(),
            signUpRequest.getLastName(),
            signUpRequest.getStudentNumber(),
            signUpRequest.getEmail(),
            (signUpRequest.getPassword() == null) ? null : passwordEncoder.encode(signUpRequest.getPassword())
        );
        
        user.setRoles(List.of());
        user.setVerified(false);
        
        // Generate verification token and send email
        String verificationToken = emailService.generateToken();
        user.setVerificationToken(verificationToken);
        
        User savedUser = saveUser(user);
        
        // Send verification email
        String verifyUrl = String.format("%s/confirm?token=%s", frontendBaseUrl, verificationToken);
        if (sendConfirmEmail) {
            emailService.sendVerificationEmail(user.getEmail(), verifyUrl);
        } else {
            log.info("Skipping email confirmation for user: {}", user.getEmail());
        }
        
        log.info("User created successfully: {}", user.getEmail());
        return savedUser;
    }

    public User createUser(SignupRequest signUpRequest) throws Exception {
        return createUser(signUpRequest, true); // Default to sending confirmation email
    }
    
    /**
     * Verify user email using verification token
     */
    public User verifyUserEmail(String verificationToken) {
        log.info("Verifying user email with token");
        
        User user = findByVerificationToken(verificationToken)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));
        
        user.setVerified(true);
        user.setVerificationToken(null);
        user.addRole(Role.ROLE_VERIFIED);
        
        User savedUser = saveUser(user);
        log.info("User email verified successfully: {}", user.getEmail());
        return savedUser;
    }

    public User verifyDirectly(UUID userId) {
        log.info("Directly verifying user with ID: {}", userId);
        
        User user = findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (user.isVerified()) {
            throw new IllegalArgumentException("User is already verified");
        }
        
        user.setVerified(true);
        user.setVerificationToken(null);
        user.addRole(Role.ROLE_VERIFIED);
        
        User savedUser = saveUser(user);
        log.info("User email verified successfully: {}", user.getEmail());
        return savedUser;
    }
    
    /**
     * Add admin role to user
     */
    public User addRole(UUID userId, Role role) {
        log.info("Adding admin role to user: {}", userId);
        
        User user = findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (user.hasRole(role)) {
            throw new IllegalArgumentException("User already has this role: " + role.getAuthority());
        }
        
        user.addRole(role);
        User savedUser = saveUser(user);
        
        log.info("Role {} added successfully to user: {}", role.getAuthority(), user.getEmail());
        return savedUser;
    }

    public User removeRole(UUID userId, Role role) {
        log.info("Removing role {} from user: {}", role.getAuthority(), userId);
        
        User user = findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (!user.hasRole(role)) {
            throw new IllegalArgumentException("User does not have this role: " + role.getAuthority());
        }
        
        user.getRoles().remove(role.getAuthority());
        User savedUser = saveUser(user);
        
        log.info("Role {} removed successfully from user: {}", role.getAuthority(), user.getEmail());
        return savedUser;
    }
    
    /**
     * Request password reset - generates token and sends email
     */
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);
        
        Optional<User> userOpt = findByEmail(email);
        if (userOpt.isEmpty()) {
            // Don't reveal if email exists for security - but still log for monitoring
            log.warn("Password reset requested for non-existent email: {}", email);
            return;
        }
        
        User user = userOpt.get();
        String resetToken = emailService.generateToken();
        user.setVerificationToken(resetToken);
        saveUser(user);
        
        // Send password reset email
        String resetUrl = String.format("%s/reset-password?token=%s", frontendBaseUrl, resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
        
        log.info("Password reset email sent to: {}", email);
    }
    
    /**
     * Validate reset token
     */
    public boolean validateResetToken(String token) {
        log.debug("Validating reset token");
        return findByVerificationToken(token).isPresent();
    }
    
    /**
     * Reset password using token
     */
    public User resetPassword(String token, String newPassword) {
        log.info("Resetting password using token");
        
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        
        User user = findByVerificationToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setVerificationToken(null);
        
        User savedUser = saveUser(user);
        log.info("Password reset successfully for user: {}", user.getEmail());
        return savedUser;
    }
    
    /**
     * Change user password (requires current password verification)
     */
    public User changePassword(User user, ChangePasswordRequest changePasswordRequest) {
        log.info("Changing password for user: {}", user.getEmail());
        
        // Verify current password
        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        
        if (changePasswordRequest.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long");
        }
        
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        User savedUser = saveUser(user);
        
        log.info("Password changed successfully for user: {}", user.getEmail());
        return savedUser;
    }
}