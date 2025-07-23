package com.ibrasoft.lensbridge.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ibrasoft.lensbridge.model.auth.*;

import com.ibrasoft.lensbridge.dto.request.*;
import com.ibrasoft.lensbridge.dto.response.*;

import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.security.LoginAttemptService;
import com.ibrasoft.lensbridge.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserService userService;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    LoginAttemptService loginAttemptService;

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
            User user = userService.findByEmail(userDetails.getUsername()).orElse(null);

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

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) throws Exception {
        try {
            userService.createUser(signUpRequest);
            return ResponseEntity.ok(new MessageResponse("User registered successfully! Please check your email to verify your account."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody(required = true) java.util.Map<String, String> payload) {
        String token = payload.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid verification token."));
        }

        try {
            userService.verifyUserEmail(token);
            return ResponseEntity.ok(new MessageResponse("Email verified successfully!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> requestPasswordReset(@RequestParam String email) {
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email is required."));
        }

        userService.requestPasswordReset(email);

        return ResponseEntity.ok(new MessageResponse("If an account with that email exists, a password reset email has been sent."));
    }

    @GetMapping("/validate-reset-token")
    public ResponseEntity<?> validateResetToken(@RequestParam String token) {
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Token is required."));
        }

        if (userService.validateResetToken(token)) {
            return ResponseEntity.ok(new MessageResponse("Token is valid."));
        } else {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired token."));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        if (token == null || token.isEmpty() || newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Token and new password are required."));
        }

        try {
            userService.resetPassword(token, newPassword);
            return ResponseEntity.ok(new MessageResponse("Password reset successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
                                            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.badRequest().body(new MessageResponse("You must be logged in to change your password."));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userService.findByEmail(userDetails.getUsername()).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("User not found."));
        }

        try {
            userService.changePassword(user, changePasswordRequest);
            return ResponseEntity.ok(new MessageResponse("Password changed successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
}
