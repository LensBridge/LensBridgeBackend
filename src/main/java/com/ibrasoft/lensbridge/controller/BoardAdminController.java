package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.request.CreateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.CreatePosterRequest;
import com.ibrasoft.lensbridge.dto.board.request.CreatePromotableSocialMediaRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdatePromotableSocialMediaRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateDeviceRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdatePosterRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateTickerRequest;
import com.ibrasoft.lensbridge.dto.board.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.dto.board.response.DeviceSummary;
import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.PromotableSocialMedia;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.PromotableSocialMediaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Admin controller for managing board content (posters, calendar events, board config, weekly content).
 * Returns raw entity data for admin console viewing/editing.
 * <p>
 * Authorization is per-endpoint by permission, not by role. There is deliberately no
 * class-level {@code @PreAuthorize}: reading a poster and moving a board's coordinates are
 * not the same privilege, and a blanket annotation is what made them the same before.
 * See {@code docs/PERMISSIONS.md}.
 */
@RestController
@RequestMapping("/api/admin/board")
@RequiredArgsConstructor
@Slf4j
public class BoardAdminController {

    private final PosterService posterService;
    private final PromotableSocialMediaService socialMediaService;
    private final BoardService boardService;
    private final AdminAuditService auditService;
    private final BoardStreamHandler boardStream;

    // ==================== Board Config Endpoints ====================

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONFIG_READ + "')")
    public ResponseEntity<List<DeviceConfig>> getAllBoardConfigs() {
        log.debug("Admin fetching all board configs");
        return ResponseEntity.ok(boardService.getAllBoardConfigs());
    }

    @GetMapping("/configs/{deviceId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONFIG_READ + "')")
    @Operation(operationId = "getAdminBoardConfig", summary = "Fetch a device config as an administrator")
    public ResponseEntity<DeviceConfig> getBoardConfig(@PathVariable UUID deviceId) {
        log.debug("Admin fetching board config for device: {}", deviceId);
        return ResponseEntity.ok(boardService.getBoardConfigOrThrow(deviceId));
    }

    @PutMapping("/configs/{deviceId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONFIG_WRITE + "')")
    public ResponseEntity<DeviceConfig> saveBoardConfig(
            @PathVariable UUID deviceId,
            @Valid @RequestBody DeviceConfig deviceConfig,
            HttpServletRequest request) {

        log.info("Admin saving board config for device: {}", deviceId);
        DeviceConfig saved = boardService.saveBoardConfig(deviceId, deviceConfig);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_BOARD_CONFIG,
                "BoardConfig", deviceId, request.getRemoteAddr());

        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/configs/{deviceId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONFIG_WRITE + "')")
    public ResponseEntity<DeviceConfig> updateBoardConfig(
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateBoardConfigRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating board config for device: {}", deviceId);
        DeviceConfig updated = boardService.updateBoardConfig(deviceId, updateRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_BOARD_CONFIG,
                "BoardConfig", deviceId, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    /**
     * Ticker-only door into the config, for holders of {@code board:ticker:write} who have no
     * business setting the board's coordinates. Anyone with {@code board:config:write} can
     * reach the same fields through the PATCH above.
     */
    @PatchMapping("/configs/{deviceId}/ticker")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_TICKER_WRITE + "')")
    @Operation(operationId = "updateBoardTicker", summary = "Update a board's scrolling messages")
    public ResponseEntity<DeviceConfig> updateTicker(
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateTickerRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating ticker for device: {}", deviceId);
        DeviceConfig updated = boardService.updateTicker(deviceId, updateRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_BOARD_TICKER,
                "BoardConfig", deviceId, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    // ==================== Weekly Content Endpoints ====================

    @GetMapping("/weekly-content")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<List<WeeklyContent>> getAllWeeklyContent() {
        log.debug("Admin fetching all weekly content");
        return ResponseEntity.ok(boardService.getAllWeeklyContent());
    }

    @GetMapping("/weekly-content/year/{year}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<List<WeeklyContent>> getWeeklyContentByYear(@PathVariable int year) {
        log.debug("Admin fetching weekly content for year: {}", year);
        return ResponseEntity.ok(boardService.getWeeklyContentByYear(year));
    }

    @GetMapping("/weekly-content/{year}/{weekNumber}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    @Operation(operationId = "getAdminWeeklyContent", summary = "Fetch weekly content as an administrator")
    public ResponseEntity<WeeklyContent> getWeeklyContent(
            @PathVariable int year,
            @PathVariable int weekNumber) {
        log.debug("Admin fetching weekly content for week {} of {}", weekNumber, year);
        return ResponseEntity.ok(boardService.getWeeklyContentOrThrow(year, weekNumber));
    }

    /**
     * Writes quotes and jummah prayer times together — they share this endpoint, so they
     * share one permission. The audit event is therefore the only record of who last moved
     * a khutbah time; see {@code docs/PERMISSIONS.md} §3.
     */
    @PutMapping("/weekly-content/{year}/{weekNumber}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_WEEKLY_WRITE + "')")
    public ResponseEntity<WeeklyContent> saveWeeklyContent(
            @PathVariable int year,
            @PathVariable int weekNumber,
            @Valid @RequestBody WeeklyContentRequest contentRequest,
            HttpServletRequest request) {

        log.info("Admin saving weekly content for week {} of {}", weekNumber, year);
        WeeklyContent saved = boardService.saveWeeklyContent(year, weekNumber, contentRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.SAVE_WEEKLY_CONTENT,
                "WeeklyContent", saved.getId(), request.getRemoteAddr());

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/weekly-content/{year}/{weekNumber}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_WEEKLY_WRITE + "')")
    public ResponseEntity<MessageResponse> deleteWeeklyContent(
            @PathVariable int year,
            @PathVariable int weekNumber,
            HttpServletRequest request) {

        log.info("Admin deleting weekly content for week {} of {}", weekNumber, year);
        UUID deletedId = boardService.deleteWeeklyContent(year, weekNumber);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.DELETE_WEEKLY_CONTENT,
                "WeeklyContent", deletedId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Weekly content deleted successfully"));
    }

    // ==================== Poster Endpoints ====================

    @GetMapping("/posters")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<List<Poster>> getAllPosters() {
        log.debug("Admin fetching all posters");
        return ResponseEntity.ok(posterService.getAllPosters());
    }

    @GetMapping("/posters/by-audience")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<List<Poster>> getPostersForAudience(@RequestParam Audience audience) {
        log.debug("Admin fetching posters for audience: {}", audience);
        return ResponseEntity.ok(posterService.getPostersForAudience(audience));
    }

    @GetMapping("/posters/{posterId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<Poster> getPosterById(@PathVariable UUID posterId) {
        log.debug("Admin fetching poster: {}", posterId);
        return ResponseEntity.ok(posterService.getPosterById(posterId));
    }

    @PostMapping(value = "/posters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_POSTER_WRITE + "')")
    public ResponseEntity<Poster> createPoster(
            // @ModelAttribute, not @RequestParam: on a complex type @RequestParam
            // makes Spring look for one parameter literally named "createRequest"
            // and convert it, which no converter can do. Binding a multipart form
            // field-by-field onto a DTO is what @ModelAttribute is for, and it is
            // also what makes springdoc document the individual fields instead of
            // a single opaque object.
            @ModelAttribute @Valid CreatePosterRequest createRequest,
            HttpServletRequest request) {

        log.info("Admin creating new poster: title={}", createRequest.getTitle());

        Poster response = posterService.createPoster(createRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.CREATE_POSTER, "Poster", response.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/posters/{posterId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_POSTER_WRITE + "')")
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
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_POSTER_WRITE + "')")
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
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_POSTER_WRITE + "')")
    public ResponseEntity<MessageResponse> deletePoster(
            @PathVariable UUID posterId,
            HttpServletRequest request) {

        log.info("Admin deleting poster: {}", posterId);
        posterService.deletePoster(posterId);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.DELETE_POSTER, "Poster", posterId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Poster deleted successfully"));
    }

    // ==================== Promotable Social Media Endpoints ====================

    @GetMapping("/socials")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<List<PromotableSocialMedia>> getAllSocials() {
        log.debug("Admin fetching all promotable social media");
        return ResponseEntity.ok(socialMediaService.getAll());
    }

    @GetMapping("/socials/by-audience")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<List<PromotableSocialMedia>> getSocialsForAudience(@RequestParam Audience audience) {
        log.debug("Admin fetching promotable social media for audience: {}", audience);
        return ResponseEntity.ok(socialMediaService.getForAudience(audience));
    }

    @GetMapping("/socials/{socialId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<PromotableSocialMedia> getSocialById(@PathVariable UUID socialId) {
        log.debug("Admin fetching promotable social media: {}", socialId);
        return ResponseEntity.ok(socialMediaService.getById(socialId));
    }

    @PostMapping("/socials")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_SOCIAL_WRITE + "')")
    public ResponseEntity<PromotableSocialMedia> createSocial(
            @Valid @RequestBody CreatePromotableSocialMediaRequest createRequest,
            HttpServletRequest request) {

        log.info("Admin creating promotable social media: type={}, name={}",
                createRequest.getType(), createRequest.getName());

        PromotableSocialMedia response = socialMediaService.create(createRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.CREATE_SOCIAL, "PromotableSocialMedia", response.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/socials/{socialId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_SOCIAL_WRITE + "')")
    public ResponseEntity<PromotableSocialMedia> updateSocial(
            @PathVariable UUID socialId,
            @Valid @RequestBody UpdatePromotableSocialMediaRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating promotable social media: {}", socialId);
        PromotableSocialMedia response = socialMediaService.update(socialId, updateRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_SOCIAL, "PromotableSocialMedia", socialId, request.getRemoteAddr());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/socials/{socialId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_SOCIAL_WRITE + "')")
    public ResponseEntity<MessageResponse> deleteSocial(
            @PathVariable UUID socialId,
            HttpServletRequest request) {

        log.info("Admin deleting promotable social media: {}", socialId);
        socialMediaService.delete(socialId);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.DELETE_SOCIAL, "PromotableSocialMedia", socialId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Promotable social media deleted successfully"));
    }

    // ==================== Calendar Event Endpoints ====================

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    @Operation(operationId = "getBoardEvents", summary = "List every board event")
    public ResponseEntity<List<BoardEvent>> getAllEvents() {
        log.debug("Admin fetching all calendar events");
        return ResponseEntity.ok(boardService.getAllEvents());
    }

    @GetMapping("/events/by-audience")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    public ResponseEntity<List<BoardEvent>> getEventsForAudience(@RequestParam Audience audience) {
        log.debug("Admin fetching calendar events for audience: {}", audience);
        return ResponseEntity.ok(boardService.getEventsForAudience(audience));
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONTENT_READ + "')")
    @Operation(operationId = "getBoardEventById", summary = "Fetch a single board event")
    public ResponseEntity<BoardEvent> getEventById(@PathVariable UUID eventId) {
        log.debug("Admin fetching calendar event: {}", eventId);
        return ResponseEntity.ok(boardService.getEventById(eventId));
    }

    @PostMapping("/events")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_EVENT_WRITE + "')")
    @Operation(operationId = "createBoardEvent", summary = "Create a board event")
    public ResponseEntity<BoardEvent> createEvent(
            @Valid @RequestBody CreateCalendarEventRequest createRequest,
            HttpServletRequest request) {

        log.info("Admin creating new calendar event: name={}", createRequest.getName());
        BoardEvent created = boardService.createEvent(createRequest);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.CREATE_CALENDAR_EVENT, "CalendarEvent", created.getId(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_EVENT_WRITE + "')")
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
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_EVENT_WRITE + "')")
    public ResponseEntity<MessageResponse> deleteEvent(
            @PathVariable UUID eventId,
            HttpServletRequest request) {

        log.info("Admin deleting calendar event: {}", eventId);
        boardService.deleteEvent(eventId);

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.DELETE_CALENDAR_EVENT, "CalendarEvent", eventId, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Calendar event deleted successfully"));
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_REFRESH + "')")
    public ResponseEntity<MessageResponse> refreshSignboard(HttpServletRequest request) {
        boardStream.refreshAll();

        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.REFRESH_BOARDS,
                "Musallah Board", null, request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Refresh sent to all MusallahBoard instances"));
    }

    @PatchMapping("/devices/{deviceId}")
    @PreAuthorize("hasAuthority('" + Permission.Authority.BOARD_CONFIG_WRITE + "')")
    public ResponseEntity<DeviceSummary> updateDevice(
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateDeviceRequest updateRequest,
            HttpServletRequest request) {

        log.info("Admin updating device: {}", deviceId);
        Device updated = boardService.updateDevice(deviceId, updateRequest);
        boardStream.configChanged(deviceId);
        auditService.logAuditEvent(getCurrentUserEmail(), AuditAction.UPDATE_BOARD_CONFIG, "Device", deviceId, request.getRemoteAddr());
        
        return ResponseEntity.ok(DeviceSummary.of(updated));
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
