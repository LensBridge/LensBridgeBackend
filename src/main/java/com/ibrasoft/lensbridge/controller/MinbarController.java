package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.minbar.PrayerSpace;
import com.ibrasoft.lensbridge.repository.sql.PrayerSpaceRepository;
import com.ibrasoft.lensbridge.service.BoardService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/minbar")
@RequiredArgsConstructor
@Slf4j
public class MinbarController {

    private final BoardService boardService;
    private final PrayerSpaceRepository prayerSpaceRepository;

    @GetMapping("/events")
    @Operation(operationId = "getMinbarEvents", summary = "List events for a one-month window")
    public ResponseEntity<List<EventView>> getEvents(
            @RequestParam Audience audience,
            @RequestParam int year,
            @RequestParam int month) {

        YearMonth ym = YearMonth.of(year, month);
        Instant rangeStart = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant rangeEnd = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        log.debug("Minbar fetching events for {} in {}-{}", audience, year, month);

        List<EventView> events = boardService.getEventsForAudienceInRange(audience, rangeStart, rangeEnd)
                .stream()
                .map(EventView::of)
                .toList();

        return ResponseEntity.ok(events);
    }

    @GetMapping("/prayer-spaces")
    @Operation(operationId = "getMinbarPrayerSpaces", summary = "List prayer spaces for an audience")
    public ResponseEntity<List<PrayerSpace>> getPrayerSpaces(@RequestParam Audience audience) {
        log.debug("Minbar fetching prayer spaces for {}", audience);
        return ResponseEntity.ok(prayerSpaceRepository.findByAudienceOrBoth(audience));
    }

    @GetMapping("/prayer-spaces/{id}")
    @Operation(operationId = "getMinbarPrayerSpaceById", summary = "Fetch a single prayer space with directions")
    public ResponseEntity<PrayerSpace> getPrayerSpaceById(@PathVariable UUID id) {
        log.debug("Minbar fetching prayer space {}", id);
        return prayerSpaceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
