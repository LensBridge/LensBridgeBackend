package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.board.request.CreateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateDeviceRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateTickerRequest;
import com.ibrasoft.lensbridge.dto.board.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.lensbridge.model.minbar.board.*;
import com.ibrasoft.lensbridge.model.minbar.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.repository.sql.BoardConfigRepository;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.repository.sql.WeeklyContentRepository;
import com.ibrasoft.lensbridge.util.Patch;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
    private final BoardStreamHandler boardStream;
    private final ObjectProvider<EventService> eventServiceProvider;

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
        boardStream.configChanged(deviceId);
        return saved;
    }

    public DeviceConfig updateBoardConfig(UUID deviceId, UpdateBoardConfigRequest request) {
        DeviceConfig existing = getBoardConfigOrThrow(deviceId);
        Patch.apply(request.getLocation(), existing::setLocation);
        Patch.apply(request.getDarkModeAfterIsha(), existing::setDarkModeAfterIsha);
        Patch.apply(request.getEnableScrollingMessage(), existing::setEnableScrollingMessage);
        Patch.apply(request.getScrollingMessages(), existing::setScrollingMessages);
        applyAgendaDuration(existing, request.getAgendaDurationSeconds());
        Patch.apply(request.getNextPrayerDurationSeconds(), existing::setNextPrayerDurationSeconds);
        DeviceConfig saved = boardConfigRepository.save(existing);
        log.info("Updated board config for device: {}", deviceId);
        boardStream.configChanged(deviceId);
        return saved;
    }

    /**
     * Stores the agenda duration, translating the {@code 0} sentinel back into null ("auto").
     * <p>
     * The 1–4 second gap is rejected here rather than on the DTO: expressing "zero or at least
     * five" in bean validation needs an {@code @AssertTrue} method, which Jackson and springdoc
     * would both surface as a phantom boolean property on the request schema.
     */
    private void applyAgendaDuration(DeviceConfig existing, Integer requested) {
        if (requested == null) return; // omitted — leave whatever is stored
        if (requested != 0 && requested < DeviceConfig.MIN_SLIDE_SECONDS) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("agendaDurationSeconds must be 0 (auto) or between "
                            + DeviceConfig.MIN_SLIDE_SECONDS + " and "
                            + DeviceConfig.MAX_SLIDE_SECONDS + " seconds"));
        }
        existing.setAgendaDurationSeconds(requested == 0 ? null : requested);
    }

    public DeviceConfig updateTicker(UUID deviceId, UpdateTickerRequest request) {
        DeviceConfig existing = getBoardConfigOrThrow(deviceId);
        Patch.apply(request.getEnableScrollingMessage(), existing::setEnableScrollingMessage);
        Patch.apply(request.getScrollingMessages(), existing::setScrollingMessages);
        DeviceConfig saved = boardConfigRepository.save(existing);
        log.info("Updated ticker for device: {}", deviceId);
        boardStream.configChanged(deviceId);
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

    /**
     * @param today the board's local date. Callers with a device in hand must pass its date,
     *              not the server's — near midnight the two disagree and the board would
     *              show the wrong week's jummah times.
     */
    public Optional<WeeklyContent> getWeeklyContentFor(LocalDate today) {
        WeekId week = WeekId.fromDate(today);
        return weeklyContentRepository.findByYearAndWeekNumber(week.getYear(), week.getWeekNumber());
    }

    /** Current week in the server's zone. For callers with no device context. */
    public Optional<WeeklyContent> getCurrentWeeklyContent() {
        return getWeeklyContentFor(LocalDate.now());
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
                        .durationSeconds(entry.durationSeconds())
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
        boardStream.contentChanged("weekly-content");
        return saved;
    }

    /** @return the id of the deleted row, so callers can record what was destroyed. */
    public UUID deleteWeeklyContent(int year, int weekNumber) {
        WeeklyContent content = weeklyContentRepository.findByYearAndWeekNumber(year, weekNumber)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Weekly content not found for week " + weekNumber + " of " + year)));
        UUID deletedId = content.getId();
        weeklyContentRepository.delete(content);
        log.info("Deleted weekly content for week {} of {}", weekNumber, year);
        boardStream.contentChanged("weekly-content");
        return deletedId;
    }

    // ==================== Events ====================

    public List<BoardEvent> getAllEvents() {
        return boardEventRepository.findAllByOrderByStartTimeAsc();
    }

    public BoardEvent getEventById(UUID eventId) {
        return boardEventRepository.findById(eventId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Event not found with id: " + eventId)));
    }

    public List<BoardEvent> getEventsForAudience(Audience audience) {
        return boardEventRepository.findByAudienceOrBoth(audience);
    }

    public List<BoardEvent> getUpcomingEventsForAudience(Audience audience) {
        return boardEventRepository.findUpcomingByAudienceOrBoth(audience, Instant.now());
    }

    public List<BoardEvent> getEventsForAudienceInRange(Audience audience, Instant rangeStart, Instant rangeEnd) {
        return boardEventRepository.findOverlappingForAudienceOrBoth(audience, rangeStart, rangeEnd);
    }

    public List<BoardEvent> getEventsForAudienceInRangeWithTicketEvents(Audience audience, Instant rangeStart, Instant rangeEnd) {
        return boardEventRepository.findOverlappingWithTicketEvent(audience, rangeStart, rangeEnd);
    }

    public BoardEvent createEvent(CreateCalendarEventRequest request) {
        BoardEvent boardEvent = BoardEvent.builder()
                .name(request.getName())
                .description(request.getDescription())
                .location(request.getLocation())
                .startTime(Instant.ofEpochMilli(request.getStartEpochMs()))
                .endTime(Instant.ofEpochMilli(request.getEndEpochMs()))
                .allDay(request.getAllDay())
                .audience(request.getAudience())
                .build();
        BoardEvent saved = boardEventRepository.save(boardEvent);
        log.info("Created event: id={}, name={}", saved.getId(), saved.getName());
        boardStream.contentChanged("events");
        return saved;
    }

    public BoardEvent updateEvent(UUID eventId, UpdateCalendarEventRequest request) {
        BoardEvent existing = getEventById(eventId);
        Patch.apply(request.getName(), existing::setName);
        Patch.apply(request.getDescription(), existing::setDescription);
        Patch.apply(request.getLocation(), existing::setLocation);
        Patch.apply(request.getStartTime(), existing::setStartTime);
        Patch.apply(request.getEndTime(), existing::setEndTime);
        Patch.apply(request.getAllDay(), existing::setAllDay);
        Patch.apply(request.getAudience(), existing::setAudience);
        BoardEvent saved = boardEventRepository.save(existing);
        log.info("Updated event: id={}", eventId);
        boardStream.contentChanged("events");
        return saved;
    }

    public void deleteEvent(UUID eventId) {
        BoardEvent boardEvent = getEventById(eventId);
        boardEventRepository.delete(boardEvent);
        log.info("Deleted event: id={}", eventId);
        boardStream.contentChanged("events");
    }

    public BoardEvent linkTicketEvent(UUID boardEventId, UUID tcketEventId) {
        EventService eventService = eventServiceProvider.getIfAvailable();
        if (eventService == null) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("tCketManage is not enabled"));
        }
        BoardEvent boardEvent = getEventById(boardEventId);
        Event tcketEvent = eventService.getEventById(tcketEventId);
        boardEvent.setEvent(tcketEvent);
        BoardEvent saved = boardEventRepository.save(boardEvent);
        log.info("Linked board event {} to tCket event {}", boardEventId, tcketEventId);
        boardStream.contentChanged("events");
        return saved;
    }

    public BoardEvent unlinkTicketEvent(UUID boardEventId) {
        BoardEvent boardEvent = getEventById(boardEventId);
        if (boardEvent.getEvent() == null) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("Board event is not linked to a ticket event"));
        }
        UUID oldTcketEventId = boardEvent.getEvent().getId();
        boardEvent.setEvent(null);
        BoardEvent saved = boardEventRepository.save(boardEvent);
        log.info("Unlinked board event {} from tCket event {}", boardEventId, oldTcketEventId);
        boardStream.contentChanged("events");
        return saved;
    }

    // ================== Device Management Changes ====================

    public Device updateDevice(UUID deviceId, UpdateDeviceRequest request) {
        Device existing = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Device not found: " + deviceId)));
        Patch.apply(request.getDisplayName(), name -> existing.setDisplayName(name.trim()));
        Patch.apply(request.getAudience(), existing::setAudience);
        log.info("Updated device: id={}", deviceId);
        return deviceRepository.save(existing);
    }

}
