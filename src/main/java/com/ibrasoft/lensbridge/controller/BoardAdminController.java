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
import com.ibrasoft.lensbridge.model.board.Audience;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

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
    public ResponseEntity<List<BoardConfig>> getAllBoardConfigs() {
        log.debug("Admin fetching all board configs");
        return ResponseEntity.ok(boardService.getAllBoardConfigs());
    }

    @GetMapping("/configs/{boardLocation}")
    public ResponseEntity<BoardConfig> getBoardConfig(@PathVariable BoardLocation boardLocation) {
        log.debug("Admin fetching board config for: {}", boardLocation);
        return ResponseEntity.ok(boardService.getBoardConfigOrThrow(boardLocation));
    }

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

    @GetMapping("/posters/by-board")
    public ResponseEntity<List<Poster>> getPostersForBoard(
            @RequestParam("board") BoardLocation boardLocation) {
        log.debug("Admin fetching posters for board: {}", boardLocation);
        return ResponseEntity.ok(posterService.getPostersForBoard(boardLocation));
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
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam("audience") Audience audience,
            @RequestParam("image") MultipartFile imageFile,
            HttpServletRequest request) {

        log.info("Admin creating new poster: title={}", title);

        CreatePosterRequest createRequest = CreatePosterRequest.builder()
                .title(title)
                .duration(duration)
                .startDate(startDate)
                .endDate(endDate)
                .audience(audience)
                .build();

        Poster response = posterService.createPoster(createRequest, imageFile);

        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.CREATE_POSTER, "Poster", response.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/posters/{posterId}")
    public ResponseEntity<Poster> updatePoster(
            @PathVariable UUID posterId,
            @Valid @RequestBody UpdatePosterRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating poster: {}", posterId);
        Poster response = posterService.updatePoster(posterId, updateRequest);

        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.UPDATE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/posters/{posterId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Poster> updatePosterImage(
            @PathVariable UUID posterId,
            @RequestParam("image") MultipartFile imageFile,
            HttpServletRequest request) {

        log.info("Admin updating poster image: {}", posterId);
        Poster response = posterService.updatePosterImage(posterId, imageFile);

        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.UPDATE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/posters/{posterId}")
    public ResponseEntity<MessageResponse> deletePoster(
            @PathVariable UUID posterId,
            HttpServletRequest request) {

        log.info("Admin deleting poster: {}", posterId);
        posterService.deletePoster(posterId);

        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.DELETE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Poster deleted successfully"));
    }

    // ==================== Calendar Event Endpoints ====================

    @GetMapping("/events")
    public ResponseEntity<List<Event>> getAllEvents() {
        log.debug("Admin fetching all calendar events");
        return ResponseEntity.ok(boardService.getAllEvents());
    }

    @GetMapping("/events/by-board")
    public ResponseEntity<List<Event>> getEventsForBoard(
            @RequestParam("board") BoardLocation boardLocation) {
        log.debug("Admin fetching calendar events for board: {}", boardLocation);
        return ResponseEntity.ok(boardService.getEventsForBoard(boardLocation));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<Event> getEventById(@PathVariable UUID eventId) {
        log.debug("Admin fetching calendar event: {}", eventId);
        return ResponseEntity.ok(boardService.getEventById(eventId));
    }

    @PostMapping("/events")
    public ResponseEntity<Event> createEvent(
            @Valid @RequestBody CreateCalendarEventRequest createRequest,
            HttpServletRequest request) {

        log.info("Admin creating new calendar event: name={}", createRequest.getName());
        Event created = boardService.createEvent(createRequest);

        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.CREATE_CALENDAR_EVENT, "CalendarEvent", created.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/events/{eventId}")
    public ResponseEntity<Event> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateCalendarEventRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating calendar event: {}", eventId);
        Event updated = boardService.updateEvent(eventId, updateRequest);

        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.UPDATE_CALENDAR_EVENT, "CalendarEvent", eventId, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<MessageResponse> deleteEvent(
            @PathVariable UUID eventId,
            HttpServletRequest request) {

        log.info("Admin deleting calendar event: {}", eventId);
        boardService.deleteEvent(eventId);

        UserDetailsImpl user = getCurrentUser();
        auditService.logAuditEvent(user.getEmail(), AdminAction.DELETE_CALENDAR_EVENT, "CalendarEvent", eventId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Calendar event deleted successfully"));
    }

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
