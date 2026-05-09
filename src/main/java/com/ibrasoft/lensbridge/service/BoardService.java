package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.request.CreateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.request.UpdateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.dto.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.*;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.repository.sql.BoardConfigRepository;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
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
    private final DeviceRepository deviceRepository;
    private final BoardEventRepository boardEventRepository;
    private final WeeklyContentRepository weeklyContentRepository;

    // ==================== Board Config ====================

    public Optional<DeviceConfig> getBoardConfig(UUID deviceId) {
        return boardConfigRepository.findById(deviceId);
    }

    public DeviceConfig getBoardConfigOrThrow(UUID deviceId) {
        return boardConfigRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Board configuration not found for device: " + deviceId)));
    }

    public DeviceConfig saveBoardConfig(UUID deviceId, DeviceConfig deviceConfig) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Device not found: " + deviceId)));
        deviceConfig.setDevice(device);
        DeviceConfig saved = boardConfigRepository.save(deviceConfig);
        log.info("Saved board config for device: {}", deviceId);
        return saved;
    }

    public DeviceConfig updateBoardConfig(UUID deviceId, UpdateBoardConfigRequest request) {
        DeviceConfig existing = getBoardConfigOrThrow(deviceId);
        Patch.apply(request.getLocation(), existing::setLocation);
        Patch.apply(request.getPosterCycleIntervalMs(), existing::setPosterCycleIntervalMs);
        Patch.apply(request.getRefreshAfterIshaMinutes(), existing::setRefreshAfterIshaMinutes);
        Patch.apply(request.getDarkModeAfterIsha(), existing::setDarkModeAfterIsha);
        Patch.apply(request.getDarkModeAfterIshaMinutes(), existing::setDarkModeAfterMaghribMinutes);
        Patch.apply(request.getEnableScrollingMessage(), existing::setEnableScrollingMessage);
        Patch.apply(request.getScrollingMessages(), existing::setScrollingMessages);
        DeviceConfig saved = boardConfigRepository.save(existing);
        log.info("Updated board config for device: {}", deviceId);
        return saved;
    }

    public List<DeviceConfig> getAllBoardConfigs() {
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
        return boardEventRepository.findAllByOrderByStartTimeAsc();
    }

    public Event getEventById(UUID eventId) {
        return boardEventRepository.findById(eventId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Event not found with id: " + eventId)));
    }

    public List<Event> getEventsForAudience(Audience audience) {
        return boardEventRepository.findByAudienceOrBoth(audience);
    }

    public List<Event> getUpcomingEventsForAudience(Audience audience) {
        return boardEventRepository.findUpcomingByAudienceOrBoth(audience, Instant.now());
    }

    public List<Event> getEventsForAudienceInRange(Audience audience, Instant rangeStart, Instant rangeEnd) {
        return boardEventRepository.findOverlappingForAudienceOrBoth(audience, rangeStart, rangeEnd);
    }

    public Event createEvent(CreateCalendarEventRequest request) {
        Event event = Event.builder()
                .name(request.getName())
                .description(request.getDescription())
                .location(request.getLocation())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
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
        Patch.apply(request.getStartTime(), existing::setStartTime);
        Patch.apply(request.getEndTime(), existing::setEndTime);
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
