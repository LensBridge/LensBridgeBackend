package com.ibrasoft.lensbridge.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.ibrasoft.lensbridge.dto.auth.request.ChangePasswordRequest;
import com.ibrasoft.lensbridge.dto.auth.request.LoginRequest;
import com.ibrasoft.lensbridge.dto.auth.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.auth.request.TokenRefreshRequest;
import com.ibrasoft.lensbridge.dto.auth.response.JwtResponse;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.auth.response.TokenRefreshResponse;
import com.ibrasoft.lensbridge.dto.auth.response.TokenValidationResponse;
import com.ibrasoft.lensbridge.exception.RefreshTokenException;
import com.ibrasoft.lensbridge.model.auth.RefreshToken;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.security.CurrentUser;
import com.ibrasoft.lensbridge.security.LoginAttemptService;
import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.service.RefreshTokenService;
import com.ibrasoft.lensbridge.service.UserService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                              HttpServletRequest request) {
        String clientKey = loginRequest.getEmail();

        if (loginAttemptService.isBlocked(clientKey)) {
            return ResponseEntity.status(429)
                    .body(new MessageResponse("Account temporarily locked due to too many failed login attempts. Please try again later."));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found in database"));

            loginAttemptService.recordSuccessfulAttempt(clientKey);

            String jwt = jwtUtils.generateJwtToken(authentication);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                    user.getId(), request.getHeader("User-Agent"), getClientIpAddress(request));

            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new JwtResponse(jwt,
                    refreshToken.getTokenHash(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getId(),
                    user.getEmail(),
                    roles));
        } catch (Exception e) {
            loginAttemptService.recordFailedAttempt(clientKey);
            throw e;
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        userService.createUser(signUpRequest);
        return ResponseEntity.ok(new MessageResponse("User registered successfully! Please check your email to verify your account."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody java.util.Map<String, String> payload) {
        String token = payload.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid verification token."));
        }
        userService.verifyUserEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully!"));
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
        }
        return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired token."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        if (token == null || token.isEmpty() || newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Token and new password are required."));
        }
        userService.resetPassword(token, newPassword);
        return ResponseEntity.ok(new MessageResponse("Password reset successfully."));
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
                                            @CurrentUser User user) {
        userService.changePassword(user, changePasswordRequest);
        refreshTokenService.revokeAllUserTokens(user.getId());
        return ResponseEntity.ok(new MessageResponse("Password changed successfully. Please log in again."));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshToken)
                .orElseThrow(() -> new RefreshTokenException(
                        "Refresh token not found. Please login again.", HttpStatus.UNAUTHORIZED));

        RefreshToken verifiedToken = refreshTokenService.verifyExpiration(refreshToken);

        UUID userId = verifiedToken.getUserId();
        User user = userService.findById(userId)
                .orElseThrow(() -> new RefreshTokenException(
                        "User not found. Please login again.", HttpStatus.UNAUTHORIZED));

        List<GrantedAuthority> authorities = new ArrayList<>(user.getRoles());
        Authentication auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        String newAccessToken = jwtUtils.generateJwtToken(auth);

        refreshTokenService.revokeRefreshToken(requestRefreshToken);
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(userId, "Token refresh", "System");

        return ResponseEntity.ok(new TokenRefreshResponse(newAccessToken, newRefreshToken.getToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) TokenRefreshRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            refreshTokenService.revokeRefreshToken(request.getRefreshToken());
        }
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    @PostMapping("/logout-all-devices")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<?> logoutAllDevices(@CurrentUser User user) {
        refreshTokenService.revokeAllUserTokens(user.getId());
        return ResponseEntity.ok(new MessageResponse("Logged out from all devices successfully"));
    }

    @GetMapping("/validate-token")
    public ResponseEntity<?> validateToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new MessageResponse("Invalid or expired token"));
        }
        User user = userService.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(new MessageResponse("User not found"));
        }
        return ResponseEntity.ok(new TokenValidationResponse(
                true,
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.isVerified(),
                user.getRoles().stream().map(Role::getAuthority).collect(Collectors.toList())
        ));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null) {
            return request.getRemoteAddr();
        }
        return xForwardedFor.split(",")[0].trim();
    }
}
