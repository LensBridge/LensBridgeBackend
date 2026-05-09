package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.model.board.Event;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.board.BoardPayloadAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/musallah")
@RequiredArgsConstructor
@Slf4j
public class MusallahBoardController {

    private final PosterService posterService;
    private final BoardService boardService;
    private final BoardPayloadAssembler payloadAssembler;

    // ==================== Board Configuration ====================

    @GetMapping("/config")
    public ResponseEntity<DeviceConfig> getBoardConfig(@RequestParam UUID deviceId) {
        log.debug("Musallah board fetching config for device: {}", deviceId);
        return boardService.getBoardConfig(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Weekly Content ====================

    @GetMapping("/weekly-content")
    public ResponseEntity<WeeklyContent> getCurrentWeeklyContent() {
        log.debug("Musallah board fetching current weekly content");
        return boardService.getCurrentWeeklyContent()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

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

    @GetMapping("/posters")
    public ResponseEntity<List<FrameDefinition>> getActivePosterFrames(@RequestParam Audience audience) {
        log.debug("Musallah board fetching active poster frames for audience: {}", audience);
        List<FrameDefinition> frames = posterService.getActivePosterFrameDefinitions(audience);
        return ResponseEntity.ok(frames);
    }

    // ==================== Calendar Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<Event>> getUpcomingEvents(@RequestParam Audience audience) {
        log.debug("Musallah board fetching upcoming events for audience: {}", audience);
        List<Event> events = boardService.getUpcomingEventsForAudience(audience);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/events/range")
    public ResponseEntity<List<Event>> getEventsInRange(
            @RequestParam Audience audience,
            @RequestParam("start") Instant rangeStart,
            @RequestParam("end") Instant rangeEnd) {
        log.debug("Musallah board fetching events for {} in range {} - {}", audience, rangeStart, rangeEnd);
        List<Event> events = boardService.getEventsForAudienceInRange(audience, rangeStart, rangeEnd);
        return ResponseEntity.ok(events);
    }

    // ==================== Combined Payload ====================

    @GetMapping("/payload")
    public ResponseEntity<MusallahBoardPayload> getBoardPayload(@RequestParam UUID deviceId) {
        log.debug("Musallah board fetching full payload for device: {}", deviceId);
        return ResponseEntity.ok(payloadAssembler.assemble(deviceId));
    }
}
