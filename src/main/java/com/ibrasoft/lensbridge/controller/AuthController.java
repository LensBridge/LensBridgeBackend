package com.ibrasoft.lensbridge.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ibrasoft.lensbridge.model.auth.*;

import com.ibrasoft.lensbridge.dto.request.*;
import com.ibrasoft.lensbridge.dto.response.*;

import com.ibrasoft.lensbridge.repository.UserRepository;
import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.security.services.EmailService;
import com.ibrasoft.lensbridge.security.LoginAttemptService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    EmailService emailService;

    @Autowired
    LoginAttemptService loginAttemptService;

    @Value("${frontend.baseurl}")
    private String frontendBaseUrl;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        String clientKey = loginRequest.getEmail();

        // Check if account is temporarily locked due to failed attempts
        if (loginAttemptService.isBlocked(clientKey)) {
            return ResponseEntity.status(429) // Too Many Requests
                    .body(new MessageResponse("Account temporarily locked due to too many failed login attempts. Please try again later."));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Check if user's email is verified
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);

            if (user != null && !user.isVerified()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Please verify your email before logging in."));
            }

            // Record successful login
            loginAttemptService.recordSuccessfulAttempt(clientKey);

            String jwt = jwtUtils.generateJwtToken(authentication);
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new JwtResponse(jwt,
                    userDetails.getFirstName(),
                    userDetails.getLastName(),
                    userDetails.getId(),
                    userDetails.getEmail(),
                    roles));
        } catch (Exception e) {
            // Record failed login attempt
            loginAttemptService.recordFailedAttempt(clientKey);
            throw e; // Let global exception handler deal with it
        }
    }

    @PostMapping("/addadmin")
    public ResponseEntity<?> addAdmin(@Valid @RequestBody UUID userID) {
        User user = userRepository.findById(userID).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("User not found."));
        }

        if (user.hasRole(Role.ROLE_ADMIN)) {
            return ResponseEntity.badRequest().body(new MessageResponse("User is already an admin."));
        }

        user.addRole(Role.ROLE_ADMIN);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Admin registered successfully!"));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) throws Exception {
        if (userRepository.existsByEmail(signUpRequest.getEmail()) ||
                userRepository.existsByStudentNumber(signUpRequest.getStudentNumber())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User(signUpRequest.getFirstName(),
                signUpRequest.getLastName(),
                signUpRequest.getStudentNumber(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        user.setRoles(List.of());

        // Generate verification token and send email
        String verificationToken = emailService.generateToken();
        user.setVerificationToken(verificationToken);
        user.setVerified(false);
        userRepository.save(user);

        // Link to frontend verification page instead of API directly
        String verifyUrl = String.format("%s/confirm?token=%s", frontendBaseUrl, verificationToken);
        emailService.sendVerificationEmail(user.getEmail(), verifyUrl);

        return ResponseEntity.ok(new MessageResponse("User registered successfully! Please check your email to verify your account."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody(required = true) java.util.Map<String, String> payload) {
        String token = payload.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid verification token."));
        }

        User user = userRepository.findByVerificationToken(token).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired verification token."));
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        user.addRole(Role.ROLE_VERIFIED);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Email verified successfully!"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> requestPasswordReset(@RequestParam String email) {
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email is required."));
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Don't reveal if email exists for security
            return ResponseEntity.ok(new MessageResponse("If an account with that email exists, a password reset email has been sent."));
        }

        String resetToken = emailService.generateToken();
        user.setVerificationToken(resetToken);
        userRepository.save(user);

        // Link to frontend password reset page
        String resetUrl = String.format("%s/reset-password?token=%s", frontendBaseUrl, resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);

        return ResponseEntity.ok(new MessageResponse("If an account with that email exists, a password reset email has been sent."));
    }

    @GetMapping("/validate-reset-token")
    public ResponseEntity<?> validateResetToken(@RequestParam String token) {
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Token is required."));
        }

        User user = userRepository.findByVerificationToken(token).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired token."));
        }

        return ResponseEntity.ok(new MessageResponse("Token is valid."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        if (token == null || token.isEmpty() || newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Token and new password are required."));
        }

        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(new MessageResponse("Password must be at least 6 characters long."));
        }

        User user = userRepository.findByVerificationToken(token).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired token."));
        }

        user.setPassword(encoder.encode(newPassword));
        user.setVerificationToken(null);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Password reset successfully."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
                                            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.badRequest().body(new MessageResponse("You must be logged in to change your password."));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("User not found."));
        }

        // Verify current password
        if (!encoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Current password is incorrect."));
        }

        if (changePasswordRequest.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(new MessageResponse("New password must be at least 6 characters long."));
        }

        user.setPassword(encoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Password changed successfully."));
    }
}
