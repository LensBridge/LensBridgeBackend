package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.request.CreateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.request.UpdateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.dto.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.*;
import com.ibrasoft.lensbridge.repository.sql.BoardConfigRepository;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;
import com.ibrasoft.lensbridge.repository.sql.WeeklyContentRepository;
import com.ibrasoft.lensbridge.util.Patch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardService {

    private final BoardConfigRepository boardConfigRepository;
    private final BoardEventRepository boardEventRepository;
    private final WeeklyContentRepository weeklyContentRepository;

    // ==================== Board Config ====================

    public Optional<BoardConfig> getBoardConfig(BoardLocation boardLocation) {
        return boardConfigRepository.findById(boardLocation);
    }

    public BoardConfig getBoardConfigOrThrow(BoardLocation boardLocation) {
        return boardConfigRepository.findById(boardLocation)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Board configuration not found for location: " + boardLocation)));
    }

    public BoardConfig saveBoardConfig(BoardConfig boardConfig) {
        BoardConfig saved = boardConfigRepository.save(boardConfig);
        log.info("Saved board config for location: {}", boardConfig.getBoardLocation());
        return saved;
    }

    public BoardConfig updateBoardConfig(BoardLocation boardLocation, UpdateBoardConfigRequest request) {
        BoardConfig existing = getBoardConfigOrThrow(boardLocation);
        Patch.apply(request.getLocation(), existing::setLocation);
        Patch.apply(request.getPosterCycleIntervalMs(), existing::setPosterCycleIntervalMs);
        Patch.apply(request.getRefreshAfterIshaMinutes(), existing::setRefreshAfterIshaMinutes);
        Patch.apply(request.getDarkModeAfterIsha(), existing::setDarkModeAfterIsha);
        Patch.apply(request.getDarkModeAfterIshaMinutes(), existing::setDarkModeAfterIshaMinutes);
        Patch.apply(request.getEnableScrollingMessage(), existing::setEnableScrollingMessage);
        Patch.apply(request.getScrollingMessages(), existing::setScrollingMessages);
        BoardConfig saved = boardConfigRepository.save(existing);
        log.info("Updated board config for location: {}", boardLocation);
        return saved;
    }

    public List<BoardConfig> getAllBoardConfigs() {
        return boardConfigRepository.findAll();
    }

    // ==================== Weekly Content ====================

    public List<WeeklyContent> getAllWeeklyContent() {
        return weeklyContentRepository.findAll();
    }

    public Optional<WeeklyContent> getWeeklyContent(int year, int weekNumber) {
        return weeklyContentRepository.findByYearAndWeekNumber(year, weekNumber);
    }

    public WeeklyContent getWeeklyContentOrThrow(int year, int weekNumber) {
        return weeklyContentRepository.findByYearAndWeekNumber(year, weekNumber)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Weekly content not found for week " + weekNumber + " of " + year)));
    }

    public Optional<WeeklyContent> getCurrentWeeklyContent() {
        WeekId current = WeekId.fromDate(LocalDate.now());
        return weeklyContentRepository.findByYearAndWeekNumber(current.getYear(), current.getWeekNumber());
    }

    public List<WeeklyContent> getWeeklyContentByYear(int year) {
        return weeklyContentRepository.findByYear(year);
    }

    @Transactional
    public WeeklyContent saveWeeklyContent(int year, int weekNumber, WeeklyContentRequest request) {
        WeeklyContent content = weeklyContentRepository.findByYearAndWeekNumber(year, weekNumber)
                .orElseGet(() -> WeeklyContent.builder().year(year).weekNumber(weekNumber).build());

        if (request.getQuotes() != null) {
            content.getQuotes().clear();
            for (WeeklyContentRequest.QuoteEntry entry : request.getQuotes()) {
                IslamicQuote quote = IslamicQuote.builder()
                        .weeklyContent(content)
                        .kind(entry.kind())
                        .arabic(entry.arabic())
                        .transliteration(entry.transliteration())
                        .translation(entry.translation())
                        .reference(entry.reference())
                        .build();
                content.getQuotes().add(quote);
            }
        }

        if (request.getJummahPrayers() != null) {
            content.getJummahPrayers().clear();
            for (WeeklyContentRequest.JummahSlot slot : request.getJummahPrayers()) {
                JummahPrayer prayer = JummahPrayer.builder()
                        .weeklyContent(content)
                        .prayerTime(slot.prayerTime() != null
                                ? java.time.LocalTime.parse(slot.prayerTime(), DateTimeFormatter.ofPattern("HH:mm"))
                                : null)
                        .khatib(slot.khatib())
                        .room(slot.room())
                        .build();
                content.getJummahPrayers().add(prayer);
            }
        }

        WeeklyContent saved = weeklyContentRepository.save(content);
        log.info("Saved weekly content for week {} of {}", weekNumber, year);
        return saved;
    }

    public void deleteWeeklyContent(int year, int weekNumber) {
        WeeklyContent content = weeklyContentRepository.findByYearAndWeekNumber(year, weekNumber)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Weekly content not found for week " + weekNumber + " of " + year)));
        weeklyContentRepository.delete(content);
        log.info("Deleted weekly content for week {} of {}", weekNumber, year);
    }

    // ==================== Events ====================

    public List<Event> getAllEvents() {
        return boardEventRepository.findAllByOrderByStartEpochMsAsc();
    }

    public Event getEventById(UUID eventId) {
        return boardEventRepository.findById(eventId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Event not found with id: " + eventId)));
    }

    public List<Event> getEventsForBoard(BoardLocation boardLocation) {
        return boardEventRepository.findByAudienceOrBoth(boardLocation.audience());
    }

    public List<Event> getUpcomingEventsForBoard(BoardLocation boardLocation) {
        long now = Instant.now().toEpochMilli();
        return boardEventRepository.findUpcomingByAudienceOrBoth(boardLocation.audience(), now);
    }

    public List<Event> getEventsForBoardInRange(BoardLocation boardLocation, long rangeStart, long rangeEnd) {
        return boardEventRepository.findOverlappingForAudienceOrBoth(boardLocation.audience(), rangeStart, rangeEnd);
    }

    public Event createEvent(CreateCalendarEventRequest request) {
        Event event = Event.builder()
                .name(request.getName())
                .description(request.getDescription())
                .location(request.getLocation())
                .startEpochMs(request.getStartEpochMs())
                .endEpochMs(request.getEndEpochMs())
                .allDay(request.getAllDay())
                .audience(request.getAudience())
                .build();
        Event saved = boardEventRepository.save(event);
        log.info("Created event: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    public Event updateEvent(UUID eventId, UpdateCalendarEventRequest request) {
        Event existing = getEventById(eventId);
        Patch.apply(request.getName(), existing::setName);
        Patch.apply(request.getDescription(), existing::setDescription);
        Patch.apply(request.getLocation(), existing::setLocation);
        Patch.apply(request.getStartEpochMs(), existing::setStartEpochMs);
        Patch.apply(request.getEndEpochMs(), existing::setEndEpochMs);
        Patch.apply(request.getAllDay(), existing::setAllDay);
        Patch.apply(request.getAudience(), existing::setAudience);
        Event saved = boardEventRepository.save(existing);
        log.info("Updated event: id={}", eventId);
        return saved;
    }

    public void deleteEvent(UUID eventId) {
        Event event = getEventById(eventId);
        boardEventRepository.delete(event);
        log.info("Deleted event: id={}", eventId);
    }
}
