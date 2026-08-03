package com.ibrasoft.lensbridge.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import com.ibrasoft.lensbridge.dto.auth.request.ResetPasswordRequest;
import com.ibrasoft.lensbridge.dto.auth.request.ValidateResetTokenRequest;
import com.ibrasoft.lensbridge.dto.auth.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.auth.request.TokenRefreshRequest;
import com.ibrasoft.lensbridge.dto.auth.request.VerifyEmailRequest;
import com.ibrasoft.lensbridge.dto.auth.response.JwtResponse;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.auth.response.TokenRefreshResponse;
import com.ibrasoft.lensbridge.dto.auth.response.TokenValidationResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
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

    @Operation(operationId = "signIn", summary = "Authenticate with email and password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated; access and refresh tokens issued"),
            @ApiResponse(responseCode = "401", description = "Bad credentials or disabled account",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts; account temporarily locked",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/signin")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                              HttpServletRequest request) {
        String clientKey = loginRequest.getEmail();

        if (loginAttemptService.isBlocked(clientKey)) {
            throw new ApiResponseException(HttpStatus.TOO_MANY_REQUESTS,
                    new MessageResponse("Account temporarily locked due to too many failed login attempts. Please try again later."));
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

    @Operation(operationId = "signUp", summary = "Register a new account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account created; verification email sent"),
            @ApiResponse(responseCode = "400", description = "Validation failed or email already registered",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        userService.createUser(signUpRequest);
        return ResponseEntity.ok(new MessageResponse("User registered successfully! Please check your email to verify your account."));
    }

    @Operation(operationId = "verifyEmail", summary = "Confirm an email address using the token from the verification email")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified"),
            @ApiResponse(responseCode = "400", description = "Missing, invalid or expired token",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        userService.verifyUserEmail(request.getToken());
        return ResponseEntity.ok(new MessageResponse("Email verified successfully!"));
    }

    @Operation(operationId = "requestPasswordReset",
            summary = "Send a password reset email",
            description = "Always reports success so the response cannot be used to probe which addresses are registered.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted"),
            @ApiResponse(responseCode = "400", description = "Email missing",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> requestPasswordReset(@RequestParam String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        userService.requestPasswordReset(email);
        return ResponseEntity.ok(new MessageResponse("If an account with that email exists, a password reset email has been sent."));
    }

    @Operation(operationId = "validateResetToken",
            summary = "Check whether a password reset token is still usable",
            description = "A POST despite being a read: the token is a credential, and only a body keeps it "
                    + "out of access logs, proxy logs and browser history. Being a POST also stops the "
                    + "result being cached by an intermediary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "400", description = "Token missing, invalid or expired",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/validate-reset-token")
    public ResponseEntity<MessageResponse> validateResetToken(@Valid @RequestBody ValidateResetTokenRequest request) {
        // Presence is enforced by bean validation; GlobalExceptionHandler renders
        // both that and the rejection below as a MessageResponse.
        if (!userService.validateResetToken(request.getToken())) {
            throw new IllegalArgumentException("Invalid or expired token.");
        }
        return ResponseEntity.ok(new MessageResponse("Token is valid."));
    }

    @Operation(operationId = "resetPassword",
            summary = "Set a new password using a reset token",
            description = "The token and password travel in the request body, not the query string: "
                    + "query strings are recorded in access logs, proxies and browser history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset"),
            @ApiResponse(responseCode = "400", description = "Token or password missing, invalid or expired",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        // Presence and length are enforced by bean validation; MethodArgumentNotValidException
        // is translated to a MessageResponse by GlobalExceptionHandler.
        userService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset successfully."));
    }

    @Operation(operationId = "changePassword",
            summary = "Change the signed-in user's password",
            description = "Revokes every refresh token for the user, so all sessions must sign in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed; all sessions revoked"),
            @ApiResponse(responseCode = "400", description = "Current password incorrect or new password rejected",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not signed in",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/change-password")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
                                            @CurrentUser User user) {
        userService.changePassword(user, changePasswordRequest);
        refreshTokenService.revokeAllUserTokens(user.getId());
        return ResponseEntity.ok(new MessageResponse("Password changed successfully. Please log in again."));
    }

    @Operation(operationId = "refreshToken",
            summary = "Exchange a refresh token for a new access token",
            description = "Rotates the refresh token: the supplied one is revoked and a new one returned.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access and refresh tokens issued"),
            @ApiResponse(responseCode = "401", description = "Refresh token unknown, expired or already revoked",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
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

    @Operation(operationId = "logout",
            summary = "Revoke a single refresh token",
            description = "Succeeds even when no token is supplied, so signing out is idempotent.")
    @ApiResponse(responseCode = "200", description = "Session ended")
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestBody(required = false) TokenRefreshRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            refreshTokenService.revokeRefreshToken(request.getRefreshToken());
        }
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    @Operation(operationId = "logoutAllDevices", summary = "Revoke every refresh token for the signed-in user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All sessions ended"),
            @ApiResponse(responseCode = "403", description = "Not signed in",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/logout-all-devices")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<MessageResponse> logoutAllDevices(@CurrentUser User user) {
        refreshTokenService.revokeAllUserTokens(user.getId());
        return ResponseEntity.ok(new MessageResponse("Logged out from all devices successfully"));
    }

    @Operation(operationId = "validateToken",
            summary = "Check the current access token and return the user it belongs to")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token valid; user returned"),
            @ApiResponse(responseCode = "401", description = "Token missing, expired, or user no longer exists",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @GetMapping("/validate-token")
    public ResponseEntity<TokenValidationResponse> validateToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiResponseException(HttpStatus.UNAUTHORIZED, new MessageResponse("Invalid or expired token"));
        }
        User user = userService.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            throw new ApiResponseException(HttpStatus.UNAUTHORIZED, new MessageResponse("User not found"));
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
