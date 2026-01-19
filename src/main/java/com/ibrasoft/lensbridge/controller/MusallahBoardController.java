package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.board.BoardConfig;
import com.ibrasoft.lensbridge.model.board.BoardLocation;
import com.ibrasoft.lensbridge.model.board.Event;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Controller for the Musallah Board display.
 * Returns data formatted specifically for rendering on the board screens.
 * These endpoints are publicly accessible (no authentication required)
 * as they're consumed by the board display hardware.
 */
@RestController
@RequestMapping("/api/musallah")
@RequiredArgsConstructor
@Slf4j
public class MusallahBoardController {

    private final PosterService posterService;
    private final BoardService boardService;

    // ==================== Board Configuration ====================

    /**
     * Get the board configuration for a specific board location.
     * Contains display settings like poster cycle interval, dark mode settings, etc.
     */
    @GetMapping("/config")
    public ResponseEntity<BoardConfig> getBoardConfig(
            @RequestParam("board") BoardLocation boardLocation) {
        log.debug("Musallah board fetching config for: {}", boardLocation);
        return boardService.getBoardConfig(boardLocation)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Weekly Content ====================

    /**
     * Get the current week's content (verse, hadith, jummah prayer info).
     * Returns 404 if no content is set for the current week.
     */
    @GetMapping("/weekly-content")
    public ResponseEntity<WeeklyContent> getCurrentWeeklyContent() {
        log.debug("Musallah board fetching current weekly content");
        return boardService.getCurrentWeeklyContent()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get weekly content for a specific week.
     */
    @GetMapping("/weekly-content/{year}/{weekNumber}")
    public ResponseEntity<WeeklyContent> getWeeklyContent(
            @PathVariable int year,
            @PathVariable int weekNumber) {
        log.debug("Musallah board fetching weekly content for week {} of {}", weekNumber, year);
        return boardService.getWeeklyContent(year, weekNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Poster Frames ====================

    /**
     * Get active poster frames for display on the board.
     * Returns FrameDefinition objects containing poster URLs and display duration.
     * Only returns posters that are currently active (within their date window)
     * and match the board's audience.
     * Sorted by startDate descending (newest first).
     */
    @GetMapping("/posters")
    public ResponseEntity<List<FrameDefinition>> getActivePosterFrames(
            @RequestParam("board") BoardLocation boardLocation) {
        log.debug("Musallah board fetching active poster frames for: {}", boardLocation);
        List<FrameDefinition> frames = posterService.getActivePosterFrameDefinitions(boardLocation);
        return ResponseEntity.ok(frames);
    }

    // ==================== Calendar Events ====================

    /**
     * Get upcoming calendar events for display on the board.
     * Returns events that match the board's audience (or BOTH),
     * filtered to only upcoming events (startTimestamp >= now).
     * Sorted by startTimestamp ascending.
     */
    @GetMapping("/events")
    public ResponseEntity<List<Event>> getUpcomingEvents(
            @RequestParam("board") BoardLocation boardLocation) {
        log.debug("Musallah board fetching upcoming events for: {}", boardLocation);
        List<Event> events = boardService.getUpcomingEventsForBoard(boardLocation);
        return ResponseEntity.ok(events);
    }

    /**
     * Get calendar events within a specific time range for display on the board.
     * Useful for "week at a glance" or daily schedule views.
     * Returns events that overlap with the given time range.
     */
    @GetMapping("/events/range")
    public ResponseEntity<List<Event>> getEventsInRange(
            @RequestParam("board") BoardLocation boardLocation,
            @RequestParam("start") long rangeStart,
            @RequestParam("end") long rangeEnd) {
        log.debug("Musallah board fetching events for {} in range {} - {}", boardLocation, rangeStart, rangeEnd);
        List<Event> events = boardService.getEventsForBoardInRange(boardLocation, rangeStart, rangeEnd);
        return ResponseEntity.ok(events);
    }

    // ==================== Combined Payload ====================

    /**
     * Get all data needed for the board in a single request.
     * Returns board config, active posters (as FrameDefinitions), upcoming events, and current week's content.
     * This reduces the number of API calls the board needs to make on refresh.
     */
    @GetMapping("/payload")
    public ResponseEntity<MusallahBoardPayload> getBoardPayload(
            @RequestParam("board") BoardLocation boardLocation) {
        log.debug("Musallah board fetching full payload for: {}", boardLocation);
        
        BoardConfig config = boardService.getBoardConfig(boardLocation).orElse(null);
        List<FrameDefinition> posterFrames = posterService.getActivePosterFrameDefinitions(boardLocation);
        
        // Get events for the current week (Sunday to Saturday)
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .with(LocalTime.MIN);
        ZonedDateTime weekEnd = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            .with(LocalTime.MAX);
        
        List<Event> upcomingEvents = boardService.getEventsForBoardInRange(
                boardLocation, 
                weekStart.toInstant().toEpochMilli(), 
                weekEnd.toInstant().toEpochMilli()
        );
        
        WeeklyContent weeklyContent = boardService.getCurrentWeeklyContent().orElse(null);
        
        MusallahBoardPayload payload = MusallahBoardPayload.builder()
                .boardConfig(config)
                .posterFrames(posterFrames)
                .upcomingEvents(upcomingEvents)
                .weeklyContent(weeklyContent)
                .build();
        
        return ResponseEntity.ok(payload);
    }

    /**
     * Payload containing all data needed for the musallah board display.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MusallahBoardPayload {
        private BoardConfig boardConfig;
        private List<FrameDefinition> posterFrames;
        private List<Event> upcomingEvents;
        private WeeklyContent weeklyContent;
    }
}
