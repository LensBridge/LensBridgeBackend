package com.ibrasoft.lensbridge.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.ibrasoft.lensbridge.service.RefreshTokenService;
import com.ibrasoft.lensbridge.model.auth.RefreshToken;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserService userService;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    LoginAttemptService loginAttemptService;

    @Autowired
    RefreshTokenService refreshTokenService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {

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
            
            // Create refresh token
            String deviceInfo = request.getHeader("User-Agent");
            String ipAddress = getClientIpAddress(request);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                userDetails.getId(), deviceInfo, ipAddress);
            
            log.debug("Created refresh token for user {}: {}", userDetails.getId(), refreshToken.getToken());
            
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new JwtResponse(jwt,
                    refreshToken.getToken(),
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

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        
        log.debug("Attempting to refresh token: {}", requestRefreshToken);

        try {
            Optional<RefreshToken> refreshTokenOpt = refreshTokenService.findByToken(requestRefreshToken);
            
            if (refreshTokenOpt.isEmpty()) {
                log.warn("Refresh token not found in database: {}", requestRefreshToken);
                return ResponseEntity.status(401)
                    .body(new MessageResponse("Refresh token is not in database. Please login again."));
            }
            
            RefreshToken refreshToken = refreshTokenOpt.get();
            log.debug("Found refresh token for user: {}", refreshToken.getUserId());
            
            // Verify token is not expired or revoked
            RefreshToken verifiedToken = refreshTokenService.verifyExpiration(refreshToken);
            
            UUID userId = verifiedToken.getUserId();
            User user = userService.findById(userId).orElse(null);
            
            if (user == null) {
                log.warn("User not found for refresh token: {}", userId);
                return ResponseEntity.status(401)
                    .body(new MessageResponse("User not found. Please login again."));
            }
            
            // Create new access token
            UserDetailsImpl userDetails = UserDetailsImpl.build(user);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
            String newAccessToken = jwtUtils.generateJwtToken(auth);
            
            // Rotate refresh token (recommended for security)
            refreshTokenService.revokeRefreshToken(requestRefreshToken);
            RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(
                userId, "Token refresh", "System");

            log.debug("Successfully refreshed token for user: {}, new refresh token: {}", userId, newRefreshToken.getToken());

            return ResponseEntity.ok(new TokenRefreshResponse(
                newAccessToken, newRefreshToken.getToken()));
                
        } catch (RuntimeException e) {
            // Handle expired or revoked tokens
            if (e.getMessage().contains("expired") || e.getMessage().contains("revoked")) {
                log.warn("Refresh token expired or revoked: {}", e.getMessage());
                return ResponseEntity.status(401)
                    .body(new MessageResponse("Refresh token expired or revoked. Please login again."));
            }
            
            // Log unexpected errors
            log.error("Error refreshing token: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new MessageResponse("An error occurred while refreshing token. Please try again."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) TokenRefreshRequest request, 
                                   Authentication authentication) {
        try {
            // Revoke refresh token if provided
            if (request != null && request.getRefreshToken() != null) {
                refreshTokenService.revokeRefreshToken(request.getRefreshToken());
            }
            
            // If user is authenticated, we could also revoke all their tokens
            if (authentication != null && authentication.isAuthenticated()) {
                // Optionally revoke all user tokens here if needed
                // UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
                // refreshTokenService.revokeAllUserTokens(userDetails.getId());
            }
            
            return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(new MessageResponse("Logged out successfully")); // Always return success for logout
        }
    }

    @PostMapping("/logout-all-devices")
    public ResponseEntity<?> logoutAllDevices(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.badRequest().body(new MessageResponse("You must be logged in."));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        refreshTokenService.revokeAllUserTokens(userDetails.getId());
        
        return ResponseEntity.ok(new MessageResponse("Logged out from all devices successfully"));
    }

    /**
     * Helper method to get client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null) {
            return request.getRemoteAddr();
        } else {
            // X-Forwarded-For can contain multiple IPs, get the first one
            return xForwardedForHeader.split(",")[0].trim();
        }
    }
}
