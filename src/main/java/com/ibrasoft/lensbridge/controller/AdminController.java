package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.audit.AuditEventDto;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.dto.auth.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.auth.response.UserInfoResponse;
import com.ibrasoft.lensbridge.dto.upload.response.AdminUploadDto;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.model.upload.EventStatus;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.Authority.ADMIN + "')")
@Slf4j
public class AdminController {

    private final UploadService uploadService;
    private final EventsService eventsService;
    private final AdminAuditService auditService;
    private final UserService userService;

    private ResponseEntity<?> executeUploadAction(UUID uploadId, HttpServletRequest request, Consumer<UUID> serviceAction, AuditAction auditAction, String successMessage) {
        serviceAction.accept(uploadId);
        this.auditService.logAuditEvent(getCurrentUserEmail(), auditAction, "Upload", uploadId, request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    private ResponseEntity<?> executeUserAction(UUID userId, HttpServletRequest request, Consumer<UUID> serviceAction, AuditAction auditAction, String successMessage) {
        serviceAction.accept(userId);
        this.auditService.logAuditEvent(getCurrentUserEmail(), auditAction, "User", userId, request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    private <T, R> ResponseEntity<?> executeCreationAction(T input, HttpServletRequest request,
                                                           Function<T, R> serviceAction, AuditAction auditAction,
                                                           String entityType, Function<R, UUID> idExtractor,
                                                           String successMessage) {
        R createdEntity = serviceAction.apply(input);
        UUID entityId = idExtractor.apply(createdEntity);
        this.auditService.logAuditEvent(getCurrentUserEmail(), auditAction, entityType, entityId, request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    @PostMapping("/create-event")
    public ResponseEntity<?> createEvent(@RequestParam("eventName") String eventName,
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
    public ResponseEntity<?> approveUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::approveUpload, AuditAction.APPROVE_UPLOAD, "Upload approved successfully");
    }

    @DeleteMapping("/upload/{uploadId}")
    public ResponseEntity<?> deleteUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::deleteUpload, AuditAction.DELETE_UPLOAD, "Upload deleted successfully");
    }

    @PostMapping("/feature-upload/{uploadId}")
    public ResponseEntity<?> featureUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::featureUpload, AuditAction.FEATURE_UPLOAD, "Upload featured successfully");
    }

    @DeleteMapping("/upload/{uploadId}/approval")
    public ResponseEntity<?> unapproveUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::unapproveUpload, AuditAction.UNAPPROVE_UPLOAD, "Upload unapproved successfully");
    }

    @DeleteMapping("/upload/{uploadId}/featured")
    public ResponseEntity<?> unfeatureUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::unfeatureUpload, AuditAction.UNFEATURE_UPLOAD, "Upload unfeatured successfully");
    }

    // Data Retrieval Operations
    @GetMapping("/uploads")
    public ResponseEntity<Page<AdminUploadDto>> getAllUploads(Pageable pageable) {
        return ResponseEntity.ok(uploadService.getAllUploadsForAdmin(pageable));
    }

    @GetMapping("/uploads/pending")
    public ResponseEntity<Page<AdminUploadDto>> getPendingUploads(Pageable pageable) {
        return ResponseEntity.ok(uploadService.getUploadsByApprovalStatus(false, pageable));
    }

    @GetMapping("/uploads/approved")
    public ResponseEntity<Page<AdminUploadDto>> getApprovedUploads(Pageable pageable) {
        return ResponseEntity.ok(uploadService.getUploadsByApprovalStatus(true, pageable));
    }

    @GetMapping("/uploads/featured")
    public ResponseEntity<Page<AdminUploadDto>> getFeaturedUploads(Pageable pageable) {
        return ResponseEntity.ok(uploadService.getUploadsByFeaturedStatus(true, pageable));
    }

    @GetMapping("/events")
    public ResponseEntity<?> getAllEvents() {
        return ResponseEntity.ok(eventsService.getAllEvents());
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('" + Role.Authority.ROOT + "')")
    public ResponseEntity<Page<UserInfoResponse>> getAllUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @PostMapping("/user/{userId}/add-role")
    @PreAuthorize("hasRole('" + Role.Authority.ROOT + "')")
    public ResponseEntity<?> addRoleToUser(@PathVariable UUID userId, @RequestBody Role role, HttpServletRequest request) {
        return executeUserAction(userId, request,
                (id) -> userService.addRole(id, role),
                AuditAction.ADD_USER_ROLE,
                "Role added successfully to user: " + userId);
    }

    @PostMapping("/user/{userId}/remove-role")
    @PreAuthorize("hasRole('" + Role.Authority.ROOT + "')")
    public ResponseEntity<?> removeRoleFromUser(@PathVariable UUID userId, @RequestBody Role role, HttpServletRequest request) {
        return executeUserAction(userId, request,
                (id) -> userService.removeRole(id, role),
                AuditAction.REMOVE_USER_ROLE,
                "Role removed successfully from user: " + userId);
    }

    @PostMapping("/user/verify")
    @PreAuthorize("hasRole('" + Role.Authority.ROOT + "')")
    public ResponseEntity<?> verifyUser(@RequestBody Map<String, UUID> payload, HttpServletRequest request) {
        return executeUserAction(payload.get("userId"), request,
                userService::verifyDirectly, AuditAction.VERIFY_USER,
                "User verified successfully");
    }

    @PostMapping("/user/create")
    @PreAuthorize("hasRole('" + Role.Authority.ROOT + "')")
    public ResponseEntity<?> createUser(@RequestBody SignupRequest signUpRequest, HttpServletRequest request) {
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

    @GetMapping("/roles")
    @PreAuthorize("hasRole('" + Role.Authority.ROOT + "')")
    public ResponseEntity<?> getAvailableRoles() {
        return ResponseEntity.ok(Role.values());
    }

    // Audit Reporting Endpoints
    @GetMapping("/audit")
    public ResponseEntity<Page<AuditEventDto>> getAuditEvents(Pageable pageable) {
        return ResponseEntity.ok(auditService.getAllAuditEvents(pageable));
    }

    @GetMapping("/audit/failed")
    public ResponseEntity<Page<AuditEventDto>> getFailedOperations(Pageable pageable) {
        return ResponseEntity.ok(auditService.getFailedOperations(pageable));
    }

    @GetMapping("/audit/upload/{uploadId}")
    public ResponseEntity<List<AuditEventDto>> getUploadAuditHistory(@PathVariable UUID uploadId) {
        return ResponseEntity.ok(auditService.getAuditEventsByEntity("Upload", uploadId));
    }

    @GetMapping("/audit/action/{action}")
    public ResponseEntity<Page<AuditEventDto>> getAuditEventsByAction(@PathVariable AuditAction action, Pageable pageable) {
        return ResponseEntity.ok(auditService.getAuditEventsByAction(action, pageable));
    }

    @GetMapping("/audit/daterange")
    public ResponseEntity<Page<AuditEventDto>> getAuditEventsByDateRange(@RequestParam Instant start, @RequestParam Instant end, Pageable pageable) {
        return ResponseEntity.ok(auditService.getAuditEventsByDateRange(start, end, pageable));
    }

    @GetMapping("/audit/actions")
    public ResponseEntity<?> getAvailableActions() {
        return ResponseEntity.ok(AuditAction.values());
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
