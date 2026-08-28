package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.minbar.request.CreatePrayerSpaceRequest;
import com.ibrasoft.lensbridge.dto.minbar.request.UpdatePrayerSpaceRequest;
import com.ibrasoft.lensbridge.dto.minbar.response.PrayerSpaceView;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.PrayerSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Managing the prayer spaces the app navigates people to.
 * <p>
 * Reads are gated on {@code board:content:read} — seeing where the musallahs are is not a
 * privilege, and the public endpoint under {@code /api/minbar} serves the same data
 * unauthenticated. What this adds over that one is the unfiltered list: the public
 * endpoint answers "what should this student see", and a console editing a sisters'
 * musallah has to be able to load it whoever is signed in.
 * <p>
 * Writes are gated on {@code board:prayerspace:write}. As elsewhere in this codebase there
 * is no class-level {@code @PreAuthorize}; see {@code docs/PERMISSIONS.md}.
 */
@RestController
@RequestMapping("/api/admin/minbar/prayer-spaces")
@RequiredArgsConstructor
@Slf4j
public class PrayerSpaceAdminController {

    private final PrayerSpaceService prayerSpaceService;
    private final AdminAuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    @Operation(operationId = "listAdminPrayerSpaces",
            summary = "List every prayer space, regardless of audience")
    public ResponseEntity<List<PrayerSpaceView>> listPrayerSpaces() {
        log.debug("Admin fetching all prayer spaces");
        return ResponseEntity.ok(prayerSpaceService.getAllSpaces());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    @Operation(operationId = "getAdminPrayerSpace", summary = "Fetch one prayer space as an administrator")
    public ResponseEntity<PrayerSpaceView> getPrayerSpace(@PathVariable UUID id) {
        log.debug("Admin fetching prayer space {}", id);
        return ResponseEntity.ok(prayerSpaceService.getSpace(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_PRAYER_SPACE_WRITE + "')")
    @Operation(operationId = "createPrayerSpace", summary = "Create a prayer space")
    public ResponseEntity<PrayerSpaceView> createPrayerSpace(
            @Valid @RequestBody CreatePrayerSpaceRequest createRequest,
            HttpServletRequest request) {

        log.info("Admin creating prayer space: {}", createRequest.getName());
        PrayerSpaceView created = prayerSpaceService.create(createRequest);

        auditService.logAuditEvent(currentUserEmail(), AuditAction.CREATE_PRAYER_SPACE,
                "PrayerSpace", created.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_PRAYER_SPACE_WRITE + "')")
    @Operation(operationId = "updatePrayerSpace",
            summary = "Update a prayer space; omitted fields are left unchanged")
    public ResponseEntity<PrayerSpaceView> updatePrayerSpace(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePrayerSpaceRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating prayer space: {}", id);
        PrayerSpaceView updated = prayerSpaceService.update(id, updateRequest);

        auditService.logAuditEvent(currentUserEmail(), AuditAction.UPDATE_PRAYER_SPACE,
                "PrayerSpace", id, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_PRAYER_SPACE_WRITE + "')")
    @Operation(operationId = "deletePrayerSpace", summary = "Delete a prayer space and its directions")
    public ResponseEntity<MessageResponse> deletePrayerSpace(
            @PathVariable UUID id,
            HttpServletRequest request) {

        log.info("Admin deleting prayer space: {}", id);
        prayerSpaceService.delete(id);

        auditService.logAuditEvent(currentUserEmail(), AuditAction.DELETE_PRAYER_SPACE,
                "PrayerSpace", id, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Prayer space deleted successfully"));
    }

    private String currentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
