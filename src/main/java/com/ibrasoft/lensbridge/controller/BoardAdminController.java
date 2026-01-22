package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.request.CreateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.request.CreatePosterRequest;
import com.ibrasoft.lensbridge.dto.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.request.UpdateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.request.UpdatePosterRequest;
import com.ibrasoft.lensbridge.dto.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.dto.response.MessageResponse;
import com.ibrasoft.lensbridge.handler.SignboardHandler;
import com.ibrasoft.lensbridge.model.audit.AdminAction;
import com.ibrasoft.lensbridge.model.board.BoardConfig;
import com.ibrasoft.lensbridge.model.board.BoardLocation;
import com.ibrasoft.lensbridge.model.board.Event;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * Get all board configurations.
     */
    @GetMapping("/configs")
    public ResponseEntity<List<BoardConfig>> getAllBoardConfigs() {
        log.debug("Admin fetching all board configs");
        return ResponseEntity.ok(boardService.getAllBoardConfigs());
    }

    /**
     * Get board configuration for a specific location.
     */
    @GetMapping("/configs/{boardLocation}")
    public ResponseEntity<BoardConfig> getBoardConfig(@PathVariable BoardLocation boardLocation) {
        log.debug("Admin fetching board config for: {}", boardLocation);
        return ResponseEntity.ok(boardService.getBoardConfigOrThrow(boardLocation));
    }

    /**
     * Create or replace a board configuration.
     */
    @PutMapping("/configs/{boardLocation}")
    public ResponseEntity<BoardConfig> saveBoardConfig(
            @PathVariable BoardLocation boardLocation,
            @Valid @RequestBody BoardConfig boardConfig,
            HttpServletRequest request) {
        
        log.info("Admin saving board config for: {}", boardLocation);
        boardConfig.setBoardLocation(boardLocation);
        BoardConfig saved = boardService.saveBoardConfig(boardConfig);
        return ResponseEntity.ok(saved);
    }

    /**
     * Update board configuration (partial update).
     */
    @PatchMapping("/configs/{boardLocation}")
    public ResponseEntity<BoardConfig> updateBoardConfig(
            @PathVariable BoardLocation boardLocation,
            @Valid @RequestBody UpdateBoardConfigRequest updateRequest,
            HttpServletRequest request) {
        
        log.info("Admin updating board config for: {}", boardLocation);
        BoardConfig updated = boardService.updateBoardConfig(boardLocation, updateRequest);
        return ResponseEntity.ok(updated);
    }

    // ==================== Weekly Content Endpoints ====================

    /**
     * Get all weekly content.
     */
    @GetMapping("/weekly-content")
    public ResponseEntity<List<WeeklyContent>> getAllWeeklyContent() {
        log.debug("Admin fetching all weekly content");
        return ResponseEntity.ok(boardService.getAllWeeklyContent());
    }

    /**
     * Get weekly content for a specific year.
     */
    @GetMapping("/weekly-content/year/{year}")
    public ResponseEntity<List<WeeklyContent>> getWeeklyContentByYear(@PathVariable int year) {
        log.debug("Admin fetching weekly content for year: {}", year);
        return ResponseEntity.ok(boardService.getWeeklyContentByYear(year));
    }

    /**
     * Get weekly content for a specific week.
     */
    @GetMapping("/weekly-content/{year}/{weekNumber}")
    public ResponseEntity<WeeklyContent> getWeeklyContent(
            @PathVariable int year,
            @PathVariable int weekNumber) {
        log.debug("Admin fetching weekly content for week {} of {}", weekNumber, year);
        return ResponseEntity.ok(boardService.getWeeklyContentOrThrow(year, weekNumber));
    }

    /**
     * Create or update weekly content.
     */
    @PutMapping("/weekly-content")
    public ResponseEntity<WeeklyContent> saveWeeklyContent(
            @Valid @RequestBody WeeklyContentRequest contentRequest,
            HttpServletRequest request) {
        
        log.info("Admin saving weekly content for week {} of {}", 
                contentRequest.getWeekNumber(), contentRequest.getYear());
        WeeklyContent saved = boardService.saveWeeklyContent(contentRequest);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete weekly content.
     */
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

    /**
     * Get all posters sorted by newest first (most recent startDate).
     */
    @GetMapping("/posters")
    public ResponseEntity<List<Poster>> getAllPosters() {
        log.debug("Admin fetching all posters");
        return ResponseEntity.ok(posterService.getAllPosters());
    }

    /**
     * Get posters for a specific board location.
     * Returns posters matching the board's audience or BOTH, sorted by newest first.
     */
    @GetMapping("/posters/by-board")
    public ResponseEntity<List<Poster>> getPostersForBoard(
            @RequestParam("board") BoardLocation boardLocation) {
        log.debug("Admin fetching posters for board: {}", boardLocation);
        return ResponseEntity.ok(posterService.getPostersForBoard(boardLocation));
    }

    /**
     * Get a single poster by ID.
     */
    @GetMapping("/posters/{posterId}")
    public ResponseEntity<Poster> getPosterById(@PathVariable UUID posterId) {
        log.debug("Admin fetching poster: {}", posterId);
        return ResponseEntity.ok(posterService.getPosterById(posterId));
    }

    /**
     * Create a new poster with an uploaded image.
     */
    @PostMapping(value = "/posters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Poster> createPoster(
            @RequestParam("title") String title,
            @RequestParam("duration") int duration,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("audience") String audience,
            @RequestParam("image") MultipartFile imageFile,
            HttpServletRequest request) {
        
        log.info("Admin creating new poster: title={}", title);

        CreatePosterRequest createRequest = CreatePosterRequest.builder()
                .title(title)
                .duration(duration)
                .startDate(java.time.LocalDate.parse(startDate))
                .endDate(java.time.LocalDate.parse(endDate))
                .audience(com.ibrasoft.lensbridge.model.board.Audience.valueOf(audience.toUpperCase()))
                .build();

        Poster response = posterService.createPoster(createRequest, imageFile);

        // Audit the action
        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.CREATE_POSTER, "Poster", response.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update poster metadata (duration, dates, audience).
     */
    @PatchMapping("/posters/{posterId}")
    public ResponseEntity<Poster> updatePoster(
            @PathVariable UUID posterId,
            @Valid @RequestBody UpdatePosterRequest updateRequest,
            HttpServletRequest request) {
        
        log.info("Admin updating poster: {}", posterId);
        Poster response = posterService.updatePoster(posterId, updateRequest);

        // Audit the action
        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.UPDATE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(response);
    }

    /**
     * Update poster image.
     */
    @PutMapping(value = "/posters/{posterId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Poster> updatePosterImage(
            @PathVariable UUID posterId,
            @RequestParam("image") MultipartFile imageFile,
            HttpServletRequest request) {
        
        log.info("Admin updating poster image: {}", posterId);
        Poster response = posterService.updatePosterImage(posterId, imageFile);

        // Audit the action
        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.UPDATE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a poster.
     */
    @DeleteMapping("/posters/{posterId}")
    public ResponseEntity<MessageResponse> deletePoster(
            @PathVariable UUID posterId,
            HttpServletRequest request) {
        
        log.info("Admin deleting poster: {}", posterId);
        posterService.deletePoster(posterId);

        // Audit the action
        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.DELETE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Poster deleted successfully"));
    }

    // ==================== Calendar Event Endpoints ====================

    /**
     * Get all calendar events.
     */
    @GetMapping("/events")
    public ResponseEntity<List<Event>> getAllEvents() {
        log.debug("Admin fetching all calendar events");
        List<Event> events = boardService.getAllEvents();
        return ResponseEntity.ok(events);
    }

    /**
     * Get calendar events for a specific board location.
     * Returns events matching the board's audience or BOTH.
     */
    @GetMapping("/events/by-board")
    public ResponseEntity<List<Event>> getEventsForBoard(
            @RequestParam("board") BoardLocation boardLocation) {
        log.debug("Admin fetching calendar events for board: {}", boardLocation);
        List<Event> events = boardService.getEventsForBoard(boardLocation);
        return ResponseEntity.ok(events);
    }

    /**
     * Get a single calendar event by ID.
     */
    @GetMapping("/events/{eventId}")
    public ResponseEntity<Event> getEventById(@PathVariable UUID eventId) {
        log.debug("Admin fetching calendar event: {}", eventId);
        Event event = boardService.getEventById(eventId);
        return ResponseEntity.ok(event);
    }

    /**
     * Create a new calendar event.
     */
    @PostMapping("/events")
    public ResponseEntity<Event> createEvent(
            @Valid @RequestBody CreateCalendarEventRequest createRequest,
            HttpServletRequest request) {
        
        log.info("Admin creating new calendar event: name={}", createRequest.getName());

        Event event = Event.builder()
                .name(createRequest.getName())
                .description(createRequest.getDescription())
                .location(createRequest.getLocation())
                .startTimestamp(createRequest.getStartTimestamp())
                .endTimestamp(createRequest.getEndTimestamp())
                .allDay(createRequest.getAllDay())
                .audience(createRequest.getAudience())
                .build();

        Event created = boardService.createEvent(event);

        // Audit the action
        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.CREATE_CALENDAR_EVENT, "CalendarEvent", created.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update a calendar event.
     */
    @PatchMapping("/events/{eventId}")
    public ResponseEntity<Event> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateCalendarEventRequest updateRequest,
            HttpServletRequest request) {
        
        log.info("Admin updating calendar event: {}", eventId);

        Event updates = Event.builder()
                .name(updateRequest.getName())
                .description(updateRequest.getDescription())
                .location(updateRequest.getLocation())
                .startTimestamp(updateRequest.getStartTimestamp() != null ? updateRequest.getStartTimestamp() : 0)
                .endTimestamp(updateRequest.getEndTimestamp() != null ? updateRequest.getEndTimestamp() : 0)
                .allDay(updateRequest.getAllDay())
                .audience(updateRequest.getAudience())
                .build();

        Event updated = boardService.updateEvent(eventId, updates);

        // Audit the action
        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.UPDATE_CALENDAR_EVENT, "CalendarEvent", eventId, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a calendar event.
     */
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<MessageResponse> deleteEvent(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        
        log.info("Admin deleting calendar event: {}", eventId);
        boardService.deleteEvent(eventId);

        // Audit the action
        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.DELETE_CALENDAR_EVENT, "CalendarEvent", eventId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Calendar event deleted successfully"));
    }

    /**
     * Refresh a MusallahBoard instance
     * @return
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshSignboard() {
        signboardHandler.sendRefreshCommand();
        return ResponseEntity.ok("Refresh command sent to MusallahBoard instances");
    }

    // ==================== Helper Methods ====================

    private UserDetailsImpl getCurrentUser() {
        return (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
