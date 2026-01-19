package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.dto.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardConfig;
import com.ibrasoft.lensbridge.model.board.BoardLocation;
import com.ibrasoft.lensbridge.model.board.Event;
import com.ibrasoft.lensbridge.model.board.WeekId;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.repository.BoardConfigRepository;
import com.ibrasoft.lensbridge.repository.EventRepository;
import com.ibrasoft.lensbridge.repository.WeeklyContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardService {

    private final BoardConfigRepository boardConfigRepository;
    private final EventRepository eventRepository;
    private final WeeklyContentRepository weeklyContentRepository;

    private static final Sort SORT_BY_START_TIMESTAMP_ASC = Sort.by(Sort.Direction.ASC, "startTimestamp");

    // ==================== Board Config Operations ====================

    /**
     * Get the board configuration for a specific board location.
     */
    public Optional<BoardConfig> getBoardConfig(BoardLocation boardLocation) {
        return boardConfigRepository.findById(boardLocation);
    }

    /**
     * Get board config or throw if not found.
     */
    public BoardConfig getBoardConfigOrThrow(BoardLocation boardLocation) {
        return boardConfigRepository.findById(boardLocation)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Board configuration not found for location: " + boardLocation)));
    }

    /**
     * Save or update a board configuration.
     */
    public BoardConfig saveBoardConfig(BoardConfig boardConfig) {
        BoardConfig saved = boardConfigRepository.save(boardConfig);
        log.info("Saved board config for location: {}", boardConfig.getBoardLocation());
        return saved;
    }

    /**
     * Update an existing board configuration with partial updates.
     */
    public BoardConfig updateBoardConfig(BoardLocation boardLocation, UpdateBoardConfigRequest request) {
        BoardConfig existing = getBoardConfigOrThrow(boardLocation);
        
        if (request.getLocation() != null) {
            existing.setLocation(request.getLocation());
        }
        if (request.getPosterCycleInterval() != null) {
            existing.setPosterCycleInterval(request.getPosterCycleInterval());
        }
        if (request.getRefreshAfterIshaaMinutes() != null) {
            existing.setRefreshAfterIshaaMinutes(request.getRefreshAfterIshaaMinutes());
        }
        if (request.getDarkModeAfterIsha() != null) {
            existing.setDarkModeAfterIsha(request.getDarkModeAfterIsha());
        }
        if (request.getDarkModeMinutesAfterIsha() != null) {
            existing.setDarkModeMinutesAfterIsha(request.getDarkModeMinutesAfterIsha());
        }
        if (request.getEnableScrollingMessage() != null) {
            existing.setEnableScrollingMessage(request.getEnableScrollingMessage());
        }
        if (request.getScrollingMessage() != null) {
            existing.setScrollingMessage(request.getScrollingMessage());
        }
        
        BoardConfig saved = boardConfigRepository.save(existing);
        log.info("Updated board config for location: {}", boardLocation);
        return saved;
    }

    /**
     * Get all board configurations.
     */
    public List<BoardConfig> getAllBoardConfigs() {
        return boardConfigRepository.findAll();
    }

    // ==================== Weekly Content Operations ====================

    /**
     * Get all weekly content.
     */
    public List<WeeklyContent> getAllWeeklyContent() {
        return weeklyContentRepository.findAll();
    }

    /**
     * Get weekly content for a specific week.
     */
    public Optional<WeeklyContent> getWeeklyContent(WeekId weekId) {
        return weeklyContentRepository.findById(weekId);
    }

    /**
     * Get weekly content for a specific year and week number.
     */
    public Optional<WeeklyContent> getWeeklyContent(int year, int weekNumber) {
        return weeklyContentRepository.findById(new WeekId(year, weekNumber));
    }

    /**
     * Get weekly content or throw if not found.
     */
    public WeeklyContent getWeeklyContentOrThrow(int year, int weekNumber) {
        WeekId weekId = new WeekId(year, weekNumber);
        return weeklyContentRepository.findById(weekId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Weekly content not found for week " + weekNumber + " of " + year)));
    }

    /**
     * Get the current week's content.
     */
    public Optional<WeeklyContent> getCurrentWeeklyContent() {
        WeekId currentWeek = WeekId.fromDate(LocalDate.now());
        return weeklyContentRepository.findById(currentWeek);
    }

    /**
     * Get weekly content for a specific year.
     */
    public List<WeeklyContent> getWeeklyContentByYear(int year) {
        return weeklyContentRepository.findByWeekIdYear(year);
    }

    /**
     * Create or update weekly content.
     */
    public WeeklyContent saveWeeklyContent(WeeklyContentRequest request) {
        WeekId weekId = new WeekId(request.getYear(), request.getWeekNumber());
        
        WeeklyContent content = weeklyContentRepository.findById(weekId)
                .orElse(WeeklyContent.builder().weekId(weekId).build());
        
        if (request.getVerse() != null) {
            content.setVerse(request.getVerse());
        }
        if (request.getHadith() != null) {
            content.setHadith(request.getHadith());
        }
        if (request.getJummahPrayer() != null) {
            content.setJummahPrayer(request.getJummahPrayer());
        }
        
        WeeklyContent saved = weeklyContentRepository.save(content);
        log.info("Saved weekly content for week {} of {}", request.getWeekNumber(), request.getYear());
        return saved;
    }

    /**
     * Delete weekly content.
     */
    public void deleteWeeklyContent(int year, int weekNumber) {
        WeekId weekId = new WeekId(year, weekNumber);
        if (!weeklyContentRepository.existsById(weekId)) {
            throw new ApiResponseException(
                    HttpStatus.NOT_FOUND,
                    ErrorResponse.of("Weekly content not found for week " + weekNumber + " of " + year));
        }
        weeklyContentRepository.deleteById(weekId);
        log.info("Deleted weekly content for week {} of {}", weekNumber, year);
    }

    // ==================== Event Operations ====================

    /**
     * Get all events.
     */
    public List<Event> getAllEvents() {
        return eventRepository.findAllByOrderByStartTimestampAsc();
    }

    /**
     * Get an event by ID.
     */
    public Event getEventById(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Event not found with id: " + eventId)));
    }

    /**
     * Get all events for a specific board location.
     * Returns events that match the board's audience or BOTH.
     */
    public List<Event> getEventsForBoard(BoardLocation boardLocation) {
        Audience audience = boardLocationToAudience(boardLocation);
        return eventRepository.findByAudienceOrBoth(audience, SORT_BY_START_TIMESTAMP_ASC);
    }

    /**
     * Get upcoming events for a specific board location.
     * Returns events with startTimestamp >= now.
     */
    public List<Event> getUpcomingEventsForBoard(BoardLocation boardLocation) {
        Audience audience = boardLocationToAudience(boardLocation);
        long nowTimestamp = Instant.now().toEpochMilli();
        return eventRepository.findUpcomingByAudienceOrBoth(audience, nowTimestamp, SORT_BY_START_TIMESTAMP_ASC);
    }

    /**
     * Get events for a specific board within a time range.
     */
    public List<Event> getEventsForBoardInRange(BoardLocation boardLocation, long rangeStart, long rangeEnd) {
        Audience audience = boardLocationToAudience(boardLocation);
        return eventRepository.findByAudienceOrBothInTimeRange(audience, rangeStart, rangeEnd, SORT_BY_START_TIMESTAMP_ASC);
    }

    /**
     * Create a new event.
     */
    public Event createEvent(Event event) {
        if (event.getId() == null) {
            event.setId(UUID.randomUUID());
        }
        Event saved = eventRepository.save(event);
        log.info("Created event: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Update an existing event.
     */
    public Event updateEvent(UUID eventId, Event updates) {
        Event existing = getEventById(eventId);
        
        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getLocation() != null) {
            existing.setLocation(updates.getLocation());
        }
        if (updates.getStartTimestamp() != 0) {
            existing.setStartTimestamp(updates.getStartTimestamp());
        }
        if (updates.getEndTimestamp() != 0) {
            existing.setEndTimestamp(updates.getEndTimestamp());
        }
        if (updates.getAllDay() != null) {
            existing.setAllDay(updates.getAllDay());
        }
        if (updates.getAudience() != null) {
            existing.setAudience(updates.getAudience());
        }

        Event saved = eventRepository.save(existing);
        log.info("Updated event: id={}", eventId);
        return saved;
    }

    /**
     * Delete an event.
     */
    public void deleteEvent(UUID eventId) {
        Event event = getEventById(eventId);
        eventRepository.delete(event);
        log.info("Deleted event: id={}", eventId);
    }

    // ==================== Helper Methods ====================

    private Audience boardLocationToAudience(BoardLocation boardLocation) {
        return switch (boardLocation) {
            case BROTHERS_MUSALLAH -> Audience.BROTHERS;
            case SISTERS_MUSALLAH -> Audience.SISTERS;
        };
    }
}
