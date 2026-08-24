package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.audit.AuditEventDto;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.dto.auth.request.CreateUserRequest;
import com.ibrasoft.lensbridge.dto.auth.request.VerifyUserRequest;
import com.ibrasoft.lensbridge.dto.auth.response.PermissionResponse;
import com.ibrasoft.lensbridge.dto.auth.response.RoleDefinitionResponse;
import com.ibrasoft.lensbridge.dto.auth.response.UserInfoResponse;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Identity administration and audit reporting.
 * <p>
 * Every endpoint carries its own {@code @PreAuthorize} — there is no class-level gate.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminAuditService auditService;
    private final UserService userService;

    private ResponseEntity<MessageResponse> executeUserAction(UUID userId, HttpServletRequest request, Consumer<UUID> serviceAction, AuditAction auditAction, String successMessage) {
        return executeUserAction(userId, request, serviceAction, auditAction, successMessage, null);
    }

    private ResponseEntity<MessageResponse> executeUserAction(UUID userId, HttpServletRequest request, Consumer<UUID> serviceAction, AuditAction auditAction, String successMessage, String details) {
        serviceAction.accept(userId);
        this.auditService.logAuditEvent(getCurrentUserEmail(), auditAction, "User", userId, request.getRemoteAddr(), details);
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    private <T, R> ResponseEntity<MessageResponse> executeCreationAction(T input, HttpServletRequest request,
                                                           Function<T, R> serviceAction, AuditAction auditAction,
                                                           String entityType, Function<R, UUID> idExtractor,
                                                           String successMessage) {
        R createdEntity = serviceAction.apply(input);
        UUID entityId = idExtractor.apply(createdEntity);
        this.auditService.logAuditEvent(getCurrentUserEmail(), auditAction, entityType, entityId, request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    // ==================== IAM Endpoints ====================

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_USER_READ + "')")
    public ResponseEntity<Page<UserInfoResponse>> getAllUsers(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @PostMapping("/user/{userId}/add-role")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_ROLE_GRANT + "')")
    public ResponseEntity<MessageResponse> addRoleToUser(@PathVariable UUID userId, @RequestBody Role role, HttpServletRequest request) {
        return executeUserAction(userId, request,
                (id) -> userService.addRole(getCurrentUserEmail(), id, role),
                AuditAction.ADD_USER_ROLE,
                "Role added successfully to user: " + userId,
                "role=" + role.name());
    }

    @PostMapping("/user/{userId}/remove-role")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_ROLE_GRANT + "')")
    public ResponseEntity<MessageResponse> removeRoleFromUser(@PathVariable UUID userId, @RequestBody Role role, HttpServletRequest request) {
        return executeUserAction(userId, request,
                (id) -> userService.removeRole(getCurrentUserEmail(), id, role),
                AuditAction.REMOVE_USER_ROLE,
                "Role removed successfully from user: " + userId,
                "role=" + role.name());
    }

    @PostMapping("/user/{userId}/grant-permission")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_ROLE_GRANT + "')")
    @Operation(operationId = "grantUserPermission", summary = "Grant a single permission directly to a user")
    public ResponseEntity<MessageResponse> grantPermission(@PathVariable UUID userId,
                                                           @RequestBody Permission permission,
                                                           HttpServletRequest request) {
        return executeUserAction(userId, request,
                (id) -> userService.grantPermission(getCurrentUserEmail(), id, permission),
                AuditAction.GRANT_PERMISSION,
                "Permission " + permission.getAuthority() + " granted to user: " + userId,
                "permission=" + permission.getAuthority());
    }

    @PostMapping("/user/{userId}/revoke-permission")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_ROLE_GRANT + "')")
    @Operation(operationId = "revokeUserPermission", summary = "Revoke a directly-granted permission from a user")
    public ResponseEntity<MessageResponse> revokePermission(@PathVariable UUID userId,
                                                            @RequestBody Permission permission,
                                                            HttpServletRequest request) {
        return executeUserAction(userId, request,
                (id) -> userService.revokePermission(getCurrentUserEmail(), id, permission),
                AuditAction.REVOKE_PERMISSION,
                "Permission " + permission.getAuthority() + " revoked from user: " + userId,
                "permission=" + permission.getAuthority());
    }

    @PostMapping("/user/verify")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_USER_WRITE + "')")
    public ResponseEntity<MessageResponse> verifyUser(@Valid @RequestBody VerifyUserRequest payload, HttpServletRequest request) {
        return executeUserAction(payload.getUserId(), request,
                userService::verifyDirectly, AuditAction.VERIFY_USER,
                "User verified successfully");
    }

    @Operation(operationId = "createUser",
            summary = "Create an account for someone else",
            description = "Omit the password — the normal case — and the account is created disabled "
                    + "and the user is emailed a password reset link; setting a password through that "
                    + "link is what enables the account. Supply a password only when the account has "
                    + "to be usable immediately: it is then enabled on creation and no email is sent.")
    @PostMapping("/user/create")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_USER_WRITE + "')")
    public ResponseEntity<MessageResponse> createUser(@Valid @RequestBody CreateUserRequest createUserRequest, HttpServletRequest request) {
        return executeCreationAction(createUserRequest, request,
                userService::createUserByAdmin,
                AuditAction.ADD_USER,
                "User",
                User::getId,
                createUserRequest.hasPassword()
                        ? "User created successfully: " + createUserRequest.getEmail()
                        : "User created and invited: " + createUserRequest.getEmail()
                                + ". The account stays disabled until they set a password from the email.");
    }

    @Operation(operationId = "getAvailableRoles",
            summary = "List assignable roles with the permissions each one confers")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_USER_READ + "')")
    public ResponseEntity<List<RoleDefinitionResponse>> getAvailableRoles() {
        return ResponseEntity.ok(Arrays.stream(Role.values())
                .map(RoleDefinitionResponse::of)
                .toList());
    }

    @Operation(operationId = "getAvailablePermissions",
            summary = "List every permission, for building a direct-grant picker")
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_USER_READ + "')")
    public ResponseEntity<List<PermissionResponse>> getAvailablePermissions() {
        return ResponseEntity.ok(Arrays.stream(Permission.values())
                .map(PermissionResponse::of)
                .toList());
    }

    // ==================== Audit Reporting ====================

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('" + Permission.Authority.AUDIT_READ + "')")
    public ResponseEntity<Page<AuditEventDto>> getAuditEvents(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(auditService.getAllAuditEvents(pageable));
    }

    @GetMapping("/audit/failed")
    @PreAuthorize("hasAuthority('" + Permission.Authority.AUDIT_READ + "')")
    public ResponseEntity<Page<AuditEventDto>> getFailedOperations(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(auditService.getFailedOperations(pageable));
    }

    @GetMapping("/audit/upload/{uploadId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.AUDIT_READ + "')")
    public ResponseEntity<List<AuditEventDto>> getUploadAuditHistory(@PathVariable UUID uploadId) {
        return ResponseEntity.ok(auditService.getAuditEventsByEntity("Upload", uploadId));
    }

    @GetMapping("/audit/action/{action}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.AUDIT_READ + "')")
    public ResponseEntity<Page<AuditEventDto>> getAuditEventsByAction(@PathVariable AuditAction action, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(auditService.getAuditEventsByAction(action, pageable));
    }

    @GetMapping("/audit/daterange")
    @PreAuthorize("hasAuthority('" + Permission.Authority.AUDIT_READ + "')")
    public ResponseEntity<Page<AuditEventDto>> getAuditEventsByDateRange(@RequestParam Instant start, @RequestParam Instant end, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(auditService.getAuditEventsByDateRange(start, end, pageable));
    }

    @Operation(operationId = "getAvailableAuditActions", summary = "List the audit action types available for filtering")
    @GetMapping("/audit/actions")
    @PreAuthorize("hasAuthority('" + Permission.Authority.AUDIT_READ + "')")
    public ResponseEntity<List<AuditAction>> getAvailableActions() {
        return ResponseEntity.ok(List.of(AuditAction.values()));
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
