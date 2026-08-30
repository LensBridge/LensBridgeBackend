package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.lensbridge.dto.minbar.response.PrayerSpaceView;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PrayerSpaceService;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/minbar")
@Slf4j
public class MinbarController {

    private final BoardService boardService;
    private final PrayerSpaceService prayerSpaceService;
    private final TicketTypeRepository ticketTypeRepository;

    MinbarController(BoardService boardService,
                     PrayerSpaceService prayerSpaceService,
                     ObjectProvider<TicketTypeRepository> ticketTypeRepoProvider) {
        this.boardService = boardService;
        this.prayerSpaceService = prayerSpaceService;
        this.ticketTypeRepository = ticketTypeRepoProvider.getIfAvailable();
    }

    @GetMapping("/events")
    @Operation(operationId = "getMinbarEvents", summary = "List events for a one-month window, enriched with ticket availability when tCketManage is enabled")
    public ResponseEntity<List<EventView>> getEvents(
            @RequestParam Audience audience,
            @RequestParam int year,
            @RequestParam int month) {

        YearMonth ym = YearMonth.of(year, month);
        Instant rangeStart = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant rangeEnd = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        log.debug("Minbar fetching events for {} in {}-{}", audience, year, month);

        List<BoardEvent> boardEvents = boardService
                .getEventsForAudienceInRangeWithTicketEvents(audience, rangeStart, rangeEnd);

        Map<UUID, List<TicketType>> ticketTypesByEvent = loadTicketTypes(boardEvents);

        List<EventView> events = boardEvents.stream()
                .map(be -> {
                    UUID tcketEventId = be.getEvent() != null ? be.getEvent().getId() : null;
                    List<TicketType> types = tcketEventId != null
                            ? ticketTypesByEvent.getOrDefault(tcketEventId, List.of())
                            : null;
                    return EventView.of(be, types);
                })
                .toList();

        return ResponseEntity.ok(events);
    }

    /**
     * Every space carries its full walking directions. The list used to omit them -- the
     * endpoint returned the entity and {@code steps} is lazy -- which forced clients into
     * a follow-up request per space just to render a directions sheet. There are single
     * digits of prayer spaces on a campus; serving them whole is cheaper than the fan-out
     * was.
     */
    @GetMapping("/prayer-spaces")
    @Operation(operationId = "getMinbarPrayerSpaces", summary = "List prayer spaces for an audience, with directions")
    public ResponseEntity<List<PrayerSpaceView>> getPrayerSpaces(@RequestParam Audience audience) {
        log.debug("Minbar fetching prayer spaces for {}", audience);
        return ResponseEntity.ok(prayerSpaceService.getSpacesForAudience(audience));
    }

    @GetMapping("/prayer-spaces/{id}")
    @Operation(operationId = "getMinbarPrayerSpaceById", summary = "Fetch a single prayer space with directions")
    public ResponseEntity<PrayerSpaceView> getPrayerSpaceById(@PathVariable UUID id) {
        log.debug("Minbar fetching prayer space {}", id);
        return ResponseEntity.ok(prayerSpaceService.getSpace(id));
    }

    private Map<UUID, List<TicketType>> loadTicketTypes(List<BoardEvent> boardEvents) {
        if (ticketTypeRepository == null) {
            return Map.of();
        }
        Set<UUID> eventIds = boardEvents.stream()
                .filter(be -> be.getEvent() != null)
                .map(be -> be.getEvent().getId())
                .collect(Collectors.toSet());
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return ticketTypeRepository.findByEvent_IdIn(eventIds).stream()
                .collect(Collectors.groupingBy(tt -> tt.getEvent().getId()));
    }
}
