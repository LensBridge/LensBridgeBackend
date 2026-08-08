package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.audit.AuditEventDto;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.dto.auth.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.auth.request.VerifyUserRequest;
import com.ibrasoft.lensbridge.dto.auth.response.PermissionResponse;
import com.ibrasoft.lensbridge.dto.auth.response.RoleDefinitionResponse;
import com.ibrasoft.lensbridge.dto.auth.response.UserInfoResponse;
import com.ibrasoft.lensbridge.dto.upload.response.AdminUploadDto;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.model.upload.EventStatus;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Media moderation, identity administration, and audit reporting.
 * <p>
 * The class-level gate covers the legacy media endpoints only — the identity and audit
 * methods below override it with their own permission. Media authorization is unchanged:
 * the same people reach the same endpoints they always did.
 * <p>
 * The permission is accepted <em>alongside</em> the role because the role check alone is
 * name-based, and a name-based check cannot see that {@code ROOT}'s bundle already contains
 * {@code media:upload:moderate}. Without this, a ROOT-only account is refused media
 * moderation while holding, on paper, every permission in the system — and the only fix
 * would be granting it {@code ROLE_ADMIN} as well, which is the hidden role coupling this
 * redesign exists to remove.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
// Hacky fix until I remove media-related permissions from the legacy ROLE_ADMIN
@PreAuthorize("hasRole('" + Role.Authority.ADMIN + "') "
        + "or hasAuthority('" + Permission.Authority.MEDIA_UPLOAD_MODERATE + "')")
@Slf4j
public class AdminController {

    private final UploadService uploadService;
    private final EventsService eventsService;
    private final AdminAuditService auditService;
    private final UserService userService;

    private ResponseEntity<MessageResponse> executeUploadAction(UUID uploadId, HttpServletRequest request, Consumer<UUID> serviceAction, AuditAction auditAction, String successMessage) {
        serviceAction.accept(uploadId);
        this.auditService.logAuditEvent(getCurrentUserEmail(), auditAction, "Upload", uploadId, request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    private ResponseEntity<MessageResponse> executeUserAction(UUID userId, HttpServletRequest request, Consumer<UUID> serviceAction, AuditAction auditAction, String successMessage) {
        return executeUserAction(userId, request, serviceAction, auditAction, successMessage, null);
    }

    /**
     * @param details what was granted or revoked. Without it the audit log cannot tell a
     *                grant of {@code board:content:read} from a grant of
     *                {@code iam:role:grant} — the two highest- and lowest-stakes actions
     *                available here produce identical rows.
     */
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

    @PostMapping("/create-event")
    @Operation(operationId = "createMediaEvent", summary = "Create a media event")
    public ResponseEntity<MessageResponse> createEvent(@RequestParam("eventName") String eventName,
                                         @RequestParam(value = "eventDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant eventDate,
                                         @RequestParam(value = "status", required = false) EventStatus status,
                                         HttpServletRequest request) {
        return executeCreationAction(null, request,
                (ignored) -> eventsService.createEvent(eventName, eventDate),
                AuditAction.CREATE_EVENT,
                "Event",
                MediaEvent::getId,
                "Event created successfully");
    }

    // Upload Management Operations
    @PostMapping("/upload/{uploadId}")
    public ResponseEntity<MessageResponse> approveUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::approveUpload, AuditAction.APPROVE_UPLOAD, "Upload approved successfully");
    }

    @DeleteMapping("/upload/{uploadId}")
    public ResponseEntity<MessageResponse> deleteUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::deleteUpload, AuditAction.DELETE_UPLOAD, "Upload deleted successfully");
    }

    @PostMapping("/feature-upload/{uploadId}")
    public ResponseEntity<MessageResponse> featureUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::featureUpload, AuditAction.FEATURE_UPLOAD, "Upload featured successfully");
    }

    @DeleteMapping("/upload/{uploadId}/approval")
    public ResponseEntity<MessageResponse> unapproveUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::unapproveUpload, AuditAction.UNAPPROVE_UPLOAD, "Upload unapproved successfully");
    }

    @DeleteMapping("/upload/{uploadId}/featured")
    public ResponseEntity<MessageResponse> unfeatureUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::unfeatureUpload, AuditAction.UNFEATURE_UPLOAD, "Upload unfeatured successfully");
    }

    // Data Retrieval Operations
    @GetMapping("/uploads")
    @Operation(operationId = "getAdminUploads", summary = "Page through every upload, any approval state")
    public ResponseEntity<Page<AdminUploadDto>> getAllUploads(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(uploadService.getAllUploadsForAdmin(pageable));
    }

    @GetMapping("/uploads/pending")
    public ResponseEntity<Page<AdminUploadDto>> getPendingUploads(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(uploadService.getUploadsByApprovalStatus(false, pageable));
    }

    @GetMapping("/uploads/approved")
    public ResponseEntity<Page<AdminUploadDto>> getApprovedUploads(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(uploadService.getUploadsByApprovalStatus(true, pageable));
    }

    @GetMapping("/uploads/featured")
    public ResponseEntity<Page<AdminUploadDto>> getFeaturedUploads(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(uploadService.getUploadsByFeaturedStatus(true, pageable));
    }

    @Operation(operationId = "getAllEventsForAdmin", summary = "List every event, including unpublished ones")
    @GetMapping("/events")
    public ResponseEntity<List<MediaEvent>> getAllEvents() {
        return ResponseEntity.ok(eventsService.getAllEvents());
    }

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

    /**
     * Grants one permission on top of a user's roles. This is the pressure valve that keeps
     * the role list short: a one-off need is a direct grant, not a new role.
     */
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

    @PostMapping("/user/create")
    @PreAuthorize("hasAuthority('" + Permission.Authority.IAM_USER_WRITE + "')")
    public ResponseEntity<MessageResponse> createUser(@RequestBody SignupRequest signUpRequest, HttpServletRequest request) {
        return executeCreationAction(signUpRequest, request,
                (signupRequest) -> {
                    User newUser = userService.createUser(signupRequest, false);
                    userService.verifyDirectly(newUser.getId());
                    userService.requestPasswordReset(newUser.getEmail());
                    return newUser;
                },
                AuditAction.ADD_USER,
                "User",
                User::getId,
                "User created successfully: " + signUpRequest.getEmail());
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

    // Audit Reporting Endpoints
    //
    // Annotated with audit:read rather than inheriting the class-level ADMIN role, because
    // the audit log now covers board and device actions too — a BOARD_ADMIN needs to read
    // it without being handed media moderation.

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
