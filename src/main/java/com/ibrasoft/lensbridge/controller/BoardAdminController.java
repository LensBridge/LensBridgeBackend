package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.request.CreateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.CreatePosterRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdatePosterRequest;
import com.ibrasoft.lensbridge.dto.board.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.handler.SignboardHandler;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin controller for managing board content (posters, calendar events, board config, weekly content).
 * Returns raw entity data for admin console viewing/editing.
 * All endpoints require ROOT role access.
 */
@RestController
@RequestMapping("/api/admin/board")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.ROOT + "')")
@Slf4j
public class BoardAdminController {

    private final PosterService posterService;
    private final BoardService boardService;
    private final AdminAuditService auditService;
    @Autowired
    private SignboardHandler signboardHandler;

    // ==================== Board Config Endpoints ====================

    @GetMapping("/configs")
    public ResponseEntity<List<DeviceConfig>> getAllBoardConfigs() {
        log.debug("Admin fetching all board configs");
        return ResponseEntity.ok(boardService.getAllBoardConfigs());
    }

    @GetMapping("/configs/{deviceId}")
    public ResponseEntity<DeviceConfig> getBoardConfig(@PathVariable UUID deviceId) {
        log.debug("Admin fetching board config for device: {}", deviceId);
        return ResponseEntity.ok(boardService.getBoardConfigOrThrow(deviceId));
    }

    @PutMapping("/configs/{deviceId}")
    public ResponseEntity<DeviceConfig> saveBoardConfig(
            @PathVariable UUID deviceId,
            @Valid @RequestBody DeviceConfig deviceConfig,
            HttpServletRequest request) {

        log.info("Admin saving board config for device: {}", deviceId);
        DeviceConfig saved = boardService.saveBoardConfig(deviceId, deviceConfig);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/configs/{deviceId}")
    public ResponseEntity<DeviceConfig> updateBoardConfig(
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateBoardConfigRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating board config for device: {}", deviceId);
        DeviceConfig updated = boardService.updateBoardConfig(deviceId, updateRequest);
        return ResponseEntity.ok(updated);
    }

    // ==================== Weekly Content Endpoints ====================

    @GetMapping("/weekly-content")
    public ResponseEntity<List<WeeklyContent>> getAllWeeklyContent() {
        log.debug("Admin fetching all weekly content");
        return ResponseEntity.ok(boardService.getAllWeeklyContent());
    }

    @GetMapping("/weekly-content/year/{year}")
    public ResponseEntity<List<WeeklyContent>> getWeeklyContentByYear(@PathVariable int year) {
        log.debug("Admin fetching weekly content for year: {}", year);
        return ResponseEntity.ok(boardService.getWeeklyContentByYear(year));
    }

    @GetMapping("/weekly-content/{year}/{weekNumber}")
    public ResponseEntity<WeeklyContent> getWeeklyContent(
            @PathVariable int year,
            @PathVariable int weekNumber) {
        log.debug("Admin fetching weekly content for week {} of {}", weekNumber, year);
        return ResponseEntity.ok(boardService.getWeeklyContentOrThrow(year, weekNumber));
    }

    @PutMapping("/weekly-content/{year}/{weekNumber}")
    public ResponseEntity<WeeklyContent> saveWeeklyContent(
            @PathVariable int year,
            @PathVariable int weekNumber,
            @Valid @RequestBody WeeklyContentRequest contentRequest,
            HttpServletRequest request) {

        log.info("Admin saving weekly content for week {} of {}", weekNumber, year);
        WeeklyContent saved = boardService.saveWeeklyContent(year, weekNumber, contentRequest);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/weekly-content/{year}/{weekNumber}")
    public ResponseEntity<MessageResponse> deleteWeeklyContent(
            @PathVariable int year,
            @PathVariable int weekNumber,
            HttpServletRequest request) {

        log.info("Admin deleting weekly content for week {} of {}", weekNumber, year);
        boardService.deleteWeeklyContent(year, weekNumber);
        return ResponseEntity.ok(new MessageResponse("Weekly content deleted successfully"));
    }

    // ==================== Poster Endpoints ====================

    @GetMapping("/posters")
    public ResponseEntity<List<Poster>> getAllPosters() {
        log.debug("Admin fetching all posters");
        return ResponseEntity.ok(posterService.getAllPosters());
    }

    @GetMapping("/posters/by-audience")
    public ResponseEntity<List<Poster>> getPostersForAudience(@RequestParam Audience audience) {
        log.debug("Admin fetching posters for audience: {}", audience);
        return ResponseEntity.ok(posterService.getPostersForAudience(audience));
    }

    @GetMapping("/posters/{posterId}")
    public ResponseEntity<Poster> getPosterById(@PathVariable UUID posterId) {
        log.debug("Admin fetching poster: {}", posterId);
        return ResponseEntity.ok(posterService.getPosterById(posterId));
    }

    @PostMapping(value = "/posters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Poster> createPoster(
            @RequestParam("title") String title,
            @RequestParam("duration") int duration,
            @RequestParam("startTime") Instant startTime,
            @RequestParam("endTime") Instant endTime,
            @RequestParam("audience") Audience audience,
            @RequestParam("image") MultipartFile imageFile,
            HttpServletRequest request) {

        log.info("Admin creating new poster: title={}", title);

        CreatePosterRequest createRequest = CreatePosterRequest.builder()
                .title(title)
                .duration(duration)
                .startTime(startTime)
                .endTime(endTime)
                .audience(audience)
                .build();

        Poster response = posterService.createPoster(createRequest, imageFile);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.CREATE_POSTER, "Poster", response.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/posters/{posterId}")
    public ResponseEntity<Poster> updatePoster(
            @PathVariable UUID posterId,
            @Valid @RequestBody UpdatePosterRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating poster: {}", posterId);
        Poster response = posterService.updatePoster(posterId, updateRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/posters/{posterId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Poster> updatePosterImage(
            @PathVariable UUID posterId,
            @RequestParam("image") MultipartFile imageFile,
            HttpServletRequest request) {

        log.info("Admin updating poster image: {}", posterId);
        Poster response = posterService.updatePosterImage(posterId, imageFile);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/posters/{posterId}")
    public ResponseEntity<MessageResponse> deletePoster(
            @PathVariable UUID posterId,
            HttpServletRequest request) {

        log.info("Admin deleting poster: {}", posterId);
        posterService.deletePoster(posterId);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.DELETE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Poster deleted successfully"));
    }

    // ==================== Calendar Event Endpoints ====================

    @GetMapping("/events")
    public ResponseEntity<List<BoardEvent>> getAllEvents() {
        log.debug("Admin fetching all calendar events");
        return ResponseEntity.ok(boardService.getAllEvents());
    }

    @GetMapping("/events/by-audience")
    public ResponseEntity<List<BoardEvent>> getEventsForAudience(@RequestParam Audience audience) {
        log.debug("Admin fetching calendar events for audience: {}", audience);
        return ResponseEntity.ok(boardService.getEventsForAudience(audience));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<BoardEvent> getEventById(@PathVariable UUID eventId) {
        log.debug("Admin fetching calendar event: {}", eventId);
        return ResponseEntity.ok(boardService.getEventById(eventId));
    }

    @PostMapping("/events")
    public ResponseEntity<BoardEvent> createEvent(
            @Valid @RequestBody CreateCalendarEventRequest createRequest,
            HttpServletRequest request) {

        log.info("Admin creating new calendar event: name={}", createRequest.getName());
        BoardEvent created = boardService.createEvent(createRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.CREATE_CALENDAR_EVENT, "CalendarEvent", created.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/events/{eventId}")
    public ResponseEntity<BoardEvent> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateCalendarEventRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating calendar event: {}", eventId);
        BoardEvent updated = boardService.updateEvent(eventId, updateRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_CALENDAR_EVENT, "CalendarEvent", eventId, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<MessageResponse> deleteEvent(
            @PathVariable UUID eventId,
            HttpServletRequest request) {

        log.info("Admin deleting calendar event: {}", eventId);
        boardService.deleteEvent(eventId);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.DELETE_CALENDAR_EVENT, "CalendarEvent", eventId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Calendar event deleted successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refreshSignboard() {
        signboardHandler.sendRefreshCommand();
        return ResponseEntity.ok("Refresh command sent to MusallahBoard instances");
    }

    // ==================== Helper Methods ====================

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
