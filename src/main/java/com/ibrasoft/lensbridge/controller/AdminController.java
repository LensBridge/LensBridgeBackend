package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.audit.AdminAction;
import com.ibrasoft.lensbridge.audit.AdminAuditService;
import com.ibrasoft.lensbridge.audit.AuditEvent;
import com.ibrasoft.lensbridge.dto.response.AdminUploadDto;
import com.ibrasoft.lensbridge.dto.response.MessageResponse;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.event.EventStatus;
import com.ibrasoft.lensbridge.service.AdminOperationService;
import com.ibrasoft.lensbridge.service.EventsService;
import com.ibrasoft.lensbridge.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.ADMIN + "')")
@Slf4j
public class AdminController {

    private final UploadService uploadService;
    private final EventsService eventsService;
    private final AdminOperationService adminOperationService;
    private final AdminAuditService auditService;

    @PostMapping("/create-event")
    public ResponseEntity<?> createEvent(@RequestParam("eventName") String eventName,
                                         @RequestParam(value = "eventDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                                         @RequestParam(value = "status", required = false) EventStatus status) {
        try {
            log.info("Admin creating event: {} for date: {}", eventName, eventDate);
            
            Event event = new Event();
            UUID generatedId = UUID.randomUUID();
            event.setId(generatedId);
            event.setName(eventName);
            event.setDate(eventDate != null ? eventDate : LocalDate.now());
            event.setStatus(status != null ? status : EventStatus.ONGOING);

            Event savedEvent = eventsService.createEvent(event);
            log.info("Event created successfully with ID: {}", savedEvent.getId());
            
            return ResponseEntity.ok(new MessageResponse("Event created successfully: " + savedEvent.getName()));
        } catch (Exception e) {
            log.error("Error creating event: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to create event: " + e.getMessage()));
        }
    }

    // Upload Management Operations
    @PostMapping("/upload/{uploadId}")
    public ResponseEntity<?> approveUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return adminOperationService.executeUploadOperation(uploadId, AdminAction.APPROVE_UPLOAD, upload -> {
            if (upload.isApproved()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Upload is already approved"));
            }
            upload.setApproved(true);
            uploadService.updateUpload(upload);
            return ResponseEntity.ok(new MessageResponse("Upload approved successfully"));
        }, request);
    }

    @DeleteMapping("/upload/{uploadId}")
    public ResponseEntity<?> deleteUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return adminOperationService.executeUploadOperation(uploadId, AdminAction.DELETE_UPLOAD, upload -> {
            uploadService.deleteUpload(uploadId);
            return ResponseEntity.ok(new MessageResponse("Upload deleted successfully"));
        }, request);
    }

    @PostMapping("/feature-upload/{uploadId}")
    public ResponseEntity<?> featureUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return adminOperationService.executeUploadOperation(uploadId, AdminAction.FEATURE_UPLOAD, upload -> {
            if (!upload.isApproved()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Cannot feature an unapproved upload. Please approve it first."));
            }
            if (upload.isFeatured()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Upload is already featured"));
            }
            upload.setFeatured(true);
            uploadService.updateUpload(upload);
            return ResponseEntity.ok(new MessageResponse("Upload featured successfully"));
        }, request);
    }

    @DeleteMapping("/upload/{uploadId}/approval")
    public ResponseEntity<?> unapproveUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return adminOperationService.executeUploadOperation(uploadId, AdminAction.UNAPPROVE_UPLOAD, upload -> {
            if (!upload.isApproved()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Upload is already unapproved"));
            }
            upload.setApproved(false);
            upload.setFeatured(false); // Can't be featured if not approved
            uploadService.updateUpload(upload);
            return ResponseEntity.ok(new MessageResponse("Upload approval removed successfully"));
        }, request);
    }

    @DeleteMapping("/upload/{uploadId}/featured")
    public ResponseEntity<?> unfeatureUpload(@PathVariable UUID uploadId, HttpServletRequest request) {
        return adminOperationService.executeUploadOperation(uploadId, AdminAction.UNFEATURE_UPLOAD, upload -> {
            if (!upload.isFeatured()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Upload is already not featured"));
            }
            upload.setFeatured(false);
            uploadService.updateUpload(upload);
            return ResponseEntity.ok(new MessageResponse("Upload featured status removed successfully"));
        }, request);
    }

    // Data Retrieval Operations
    @GetMapping("/uploads")
    public ResponseEntity<?> getAllUploads(Pageable pageable) {
        try {
            log.debug("Admin retrieving all uploads with user information, page: {}", pageable.getPageNumber());
            Page<AdminUploadDto> uploads = uploadService.getAllUploadsForAdmin(pageable);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            log.error("Error retrieving uploads: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve uploads: " + e.getMessage()));
        }
    }

    @GetMapping("/uploads/pending")
    public ResponseEntity<?> getPendingUploads(Pageable pageable) {
        try {
            log.debug("Admin retrieving pending uploads, page: {}", pageable.getPageNumber());
            Page<AdminUploadDto> uploads = uploadService.getUploadsByApprovalStatus(false, pageable);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            log.error("Error retrieving pending uploads: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve pending uploads: " + e.getMessage()));
        }
    }

    @GetMapping("/uploads/approved")
    public ResponseEntity<?> getApprovedUploads(Pageable pageable) {
        try {
            log.debug("Admin retrieving approved uploads, page: {}", pageable.getPageNumber());
            Page<AdminUploadDto> uploads = uploadService.getUploadsByApprovalStatus(true, pageable);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            log.error("Error retrieving approved uploads: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve approved uploads: " + e.getMessage()));
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve featured uploads: " + e.getMessage()));
        }
    }

    @GetMapping("/events")
    public ResponseEntity<?> getAllEvents() {
        try {
            log.debug("Admin retrieving all events");
            return ResponseEntity.ok(eventsService.getAllEvents());
        } catch (Exception e) {
            log.error("Error retrieving events: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve events: " + e.getMessage()));
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve audit events: " + e.getMessage()));
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve failed operations: " + e.getMessage()));
        }
    }

    @GetMapping("/audit/upload/{uploadId}")
    public ResponseEntity<?> getUploadAuditHistory(@PathVariable UUID uploadId) {
        try {
            log.debug("Admin retrieving audit history for upload: {}", uploadId);
            return ResponseEntity.ok(auditService.getAuditEventsByEntity("Upload", uploadId));
        } catch (Exception e) {
            log.error("Error retrieving upload audit history: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve upload audit history: " + e.getMessage()));
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve audit events: " + e.getMessage()));
        }
    }

    @GetMapping("/audit/daterange")
    public ResponseEntity<?> getAuditEventsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Pageable pageable) {
        try {
            log.debug("Admin retrieving audit events between {} and {}", start, end);
            Page<AuditEvent> events = auditService.getAuditEventsByDateRange(start, end, pageable);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error retrieving audit events by date range: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve audit events: " + e.getMessage()));
        }
    }

    @GetMapping("/audit/actions")
    public ResponseEntity<?> getAvailableActions() {
        try {
            log.debug("Admin retrieving available audit actions");
            return ResponseEntity.ok(AdminAction.values());
        } catch (Exception e) {
            log.error("Error retrieving available actions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to retrieve available actions: " + e.getMessage()));
        }
    }
}
