package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.audit.AdminAction;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.model.audit.AuditEvent;
import com.ibrasoft.lensbridge.dto.request.SignupRequest;
import com.ibrasoft.lensbridge.dto.response.AdminUploadDto;
import com.ibrasoft.lensbridge.dto.response.MessageResponse;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.ADMIN + "')")
@Slf4j
public class AdminController {

    private final UploadService uploadService;
    private final EventsService eventsService;
    private final AdminAuditService auditService;
    private final UserService userService;

    private ResponseEntity<?> executeUploadAction(UUID uploadId, HttpServletRequest request, Consumer<UUID> serviceAction, AdminAction auditAction, String successMessage) {
        serviceAction.accept(uploadId);
        UserDetailsImpl curr = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        this.auditService.logAuditEvent(curr.getEmail(), auditAction, "Upload", uploadId, request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    private ResponseEntity<?> executeUserAction(UUID userId, HttpServletRequest request, Consumer<UUID> serviceAction, AdminAction auditAction, String successMessage) {
        serviceAction.accept(userId);
        UserDetailsImpl curr = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        this.auditService.logAuditEvent(curr.getEmail(), auditAction, "User", userId, request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    private <T, R> ResponseEntity<?> executeCreationAction(T input, HttpServletRequest request,
                                                           Function<T, R> serviceAction, AdminAction auditAction,
                                                           String entityType, Function<R, UUID> idExtractor,
                                                           String successMessage) {
        R createdEntity = serviceAction.apply(input);
        UserDetailsImpl curr = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID entityId = idExtractor.apply(createdEntity);

        this.auditService.logAuditEvent(curr.getEmail(), auditAction, entityType, entityId, request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(successMessage));
    }

    @PostMapping("/create-event")
    public ResponseEntity<?> createEvent(@RequestParam("eventName") String eventName,
                                         @RequestParam(value = "eventDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime eventDate,
                                         @RequestParam(value = "status", required = false) EventStatus status,
                                         HttpServletRequest request) {

        return executeCreationAction(null, request,
                (ignored) -> eventsService.createEvent(eventName, eventDate),
                AdminAction.CREATE_EVENT,
                "Event",
                Event::getId,
                "Event created successfully");
    }

    // Upload Management Operations
    @PostMapping("/upload/{uploadId}")
    public ResponseEntity<?> approveUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::approveUpload, AdminAction.APPROVE_UPLOAD, "Upload approved successfully");
    }

    @DeleteMapping("/upload/{uploadId}")
    public ResponseEntity<?> deleteUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::deleteUpload, AdminAction.DELETE_UPLOAD, "Upload deleted successfully");
    }

    @PostMapping("/feature-upload/{uploadId}")
    public ResponseEntity<?> featureUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::featureUpload, AdminAction.FEATURE_UPLOAD, "Upload featured successfully");
    }

    @DeleteMapping("/upload/{uploadId}/approval")
    public ResponseEntity<?> unapproveUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::unapproveUpload, AdminAction.UNAPPROVE_UPLOAD, "Upload unapproved successfully");
    }

    @DeleteMapping("/upload/{uploadId}/featured")
    public ResponseEntity<?> unfeatureUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return executeUploadAction(uploadId, request, uploadService::unfeatureUpload, AdminAction.UNFEATURE_UPLOAD, "Upload unfeatured successfully");
    }

    // Data Retrieval Operations
    @GetMapping("/uploads")
    public ResponseEntity<?> getAllUploads(Pageable pageable) {
        log.debug("Admin retrieving all uploads with user information, page: {}", pageable.getPageNumber());
        Page<AdminUploadDto> uploads = uploadService.getAllUploadsForAdmin(pageable);
        return ResponseEntity.ok(uploads);
    }

    @GetMapping("/uploads/pending")
    public ResponseEntity<?> getPendingUploads(Pageable pageable) {
        log.debug("Admin retrieving pending uploads, page: {}", pageable.getPageNumber());
        Page<AdminUploadDto> uploads = uploadService.getUploadsByApprovalStatus(false, pageable);
        return ResponseEntity.ok(uploads);
    }

    @GetMapping("/uploads/approved")
    public ResponseEntity<?> getApprovedUploads(Pageable pageable) {
        try {
            log.debug("Admin retrieving approved uploads, page: {}", pageable.getPageNumber());
            Page<AdminUploadDto> uploads = uploadService.getUploadsByApprovalStatus(true, pageable);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            log.error("Error retrieving approved uploads: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve approved uploads: " + e.getMessage()));
        }
    }

    @GetMapping("/uploads/featured")
    public ResponseEntity<?> getFeaturedUploads(Pageable pageable) {
        try {
            log.debug("Admin retrieving featured uploads, page: {}", pageable.getPageNumber());
            Page<AdminUploadDto> uploads = uploadService.getUploadsByFeaturedStatus(true, pageable);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            log.error("Error retrieving featured uploads: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve featured uploads: " + e.getMessage()));
        }
    }

    @GetMapping("/events")
    public ResponseEntity<?> getAllEvents() {
        try {
            log.debug("Admin retrieving all events");
            return ResponseEntity.ok(eventsService.getAllEvents());
        } catch (Exception e) {
            log.error("Error retrieving events: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve events: " + e.getMessage()));
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('" + Role.ROOT + "')")
    public ResponseEntity<?> getAllUsers(Pageable pageable) {
        try {
            log.debug("Admin retrieving all users, page: {}", pageable.getPageNumber());
            return ResponseEntity.ok(userService.getAllUsers(pageable));
        } catch (Exception e) {
            log.error("Error retrieving users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve users: " + e.getMessage()));
        }
    }

    @PostMapping("/user/{userId}/add-role")
    @PreAuthorize("hasRole('" + Role.ROOT + "')")
    public ResponseEntity<?> addRoleToUser(@PathVariable UUID userId, @RequestBody Role role, HttpServletRequest request) {
        return executeUserAction(userId, request,
                (id) -> {
                    log.debug("Admin adding role {} to user: {}", role, id);
                    userService.addRole(id, role);
                },
                AdminAction.ADD_USER_ROLE,
                "Role added successfully to user: " + userId);
    }

    @PostMapping("/user/{userId}/remove-role")
    @PreAuthorize("hasRole('" + Role.ROOT + "')")
    public ResponseEntity<?> removeRoleFromUser(@PathVariable UUID userId, @RequestBody Role role, HttpServletRequest request) {
        return executeUserAction(userId, request,
                (id) -> {
                    log.debug("Admin removing role {} to user: {}", role, id);
                    userService.addRole(id, role);
                },
                AdminAction.REMOVE_USER_ROLE,
                "Role added successfully to user: " + userId);
    }

    @PostMapping("/user/verify")
    @PreAuthorize("hasRole('" + Role.ROOT + "')")
    public ResponseEntity<?> verifyUser(@RequestBody Map<String, UUID> payload, HttpServletRequest request) {
        return executeUserAction(payload.get("userId"), request,
                userService::verifyDirectly, AdminAction.VERIFY_USER,
                "User verified successfully");
    }

    @PostMapping("/user/create")
    @PreAuthorize("hasRole('" + Role.ROOT + "')")
    public ResponseEntity<?> createUser(@RequestBody SignupRequest signUpRequest, HttpServletRequest request) {
        return executeCreationAction(signUpRequest, request,
                (signupRequest) -> {
                    log.debug("Admin creating user with email: {}", signupRequest.getEmail());
                    User newUser = userService.createUser(signupRequest, false);
                    userService.verifyDirectly(newUser.getId());
                    userService.requestPasswordReset(newUser.getEmail());
                    return newUser;
                },
                AdminAction.ADD_USER,
                "User",
                User::getId,
                "User created successfully: " + signUpRequest.getEmail());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('" + Role.ROOT + "')")
    public ResponseEntity<?> getAvailableRoles() {
        try {
            log.debug("Admin retrieving available roles");
            return ResponseEntity.ok(Role.values());
        } catch (Exception e) {
            log.error("Error retrieving roles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve roles: " + e.getMessage()));
        }
    }

    // Audit Reporting Endpoints
    @GetMapping("/audit")
    public ResponseEntity<?> getAuditEvents(Pageable pageable) {
        try {
            log.debug("Admin retrieving audit events, page: {}", pageable.getPageNumber());
            Page<AuditEvent> auditEvents = auditService.getAllAuditEvents(pageable);
            return ResponseEntity.ok(auditEvents);
        } catch (Exception e) {
            log.error("Error retrieving audit events: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve audit events: " + e.getMessage()));
        }
    }

    @GetMapping("/audit/failed")
    public ResponseEntity<?> getFailedOperations(Pageable pageable) {
        try {
            log.debug("Admin retrieving failed operations");
            Page<AuditEvent> failedOps = auditService.getFailedOperations(pageable);
            return ResponseEntity.ok(failedOps);
        } catch (Exception e) {
            log.error("Error retrieving failed operations: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve failed operations: " + e.getMessage()));
        }
    }

    @GetMapping("/audit/upload/{uploadId}")
    public ResponseEntity<?> getUploadAuditHistory(@PathVariable UUID uploadId) {
        try {
            log.debug("Admin retrieving audit history for upload: {}", uploadId);
            return ResponseEntity.ok(auditService.getAuditEventsByEntity("Upload", uploadId));
        } catch (Exception e) {
            log.error("Error retrieving upload audit history: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve upload audit history: " + e.getMessage()));
        }
    }

    @GetMapping("/audit/action/{action}")
    public ResponseEntity<?> getAuditEventsByAction(@PathVariable AdminAction action, Pageable pageable) {
        try {
            log.debug("Admin retrieving audit events for action: {}", action);
            Page<AuditEvent> events = auditService.getAuditEventsByAction(action, pageable);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error retrieving audit events by action: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve audit events: " + e.getMessage()));
        }
    }

    @GetMapping("/audit/daterange")
    public ResponseEntity<?> getAuditEventsByDateRange(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end, Pageable pageable) {
        try {
            log.debug("Admin retrieving audit events between {} and {}", start, end);
            Page<AuditEvent> events = auditService.getAuditEventsByDateRange(start, end, pageable);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error retrieving audit events by date range: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve audit events: " + e.getMessage()));
        }
    }

    @GetMapping("/audit/actions")
    public ResponseEntity<?> getAvailableActions() {
        try {
            log.debug("Admin retrieving available audit actions");
            return ResponseEntity.ok(AdminAction.values());
        } catch (Exception e) {
            log.error("Error retrieving available actions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to retrieve available actions: " + e.getMessage()));
        }
    }
}
