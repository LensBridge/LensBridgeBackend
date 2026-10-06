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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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

    // ==================== Board Config ====================

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
        // The row is keyed by the device (@MapsId), so the path decides which row this writes,
        // never the body: a body without an id would otherwise insert a clashing row, and a
        // body carrying another device's id would overwrite that device's config.
        deviceConfig.setId(deviceId);
        deviceConfig.setDevice(device);
        Location location = deviceConfig.getLocation();
        if (location != null) {
            // A PUT replaces the whole config, so there is nothing stored to fall back on:
            // the coordinates have to be in the body or the board has no place to calculate for.
            if (location.getLatitude() == null || location.getLongitude() == null) {
                throw badRequest("location.latitude and location.longitude are required");
            }
            validateLocation(location);
        }
        DeviceConfig saved = boardConfigRepository.save(deviceConfig);
        log.info("Saved board config for device: {}", deviceId);
        boardStream.configChanged(deviceId);
        return saved;
    }

    public DeviceConfig updateBoardConfig(UUID deviceId, UpdateBoardConfigRequest request) {
        DeviceConfig existing = getBoardConfigOrThrow(deviceId);
        mergeLocation(existing, request.getLocation());
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
     * Merges a partial location into the stored one, field by field. Replacing the whole
     * embeddable would reset every field the request left out: a body with only a timezone
     * would zero the coordinates and silently shift every prayer time on the board.
     * <p>
     * The merged result is validated before anything is written to the entity, so a rejected
     * request changes nothing.
     */
    private void mergeLocation(DeviceConfig existing, Location patch) {
        if (patch == null) return;
        Location current = existing.getLocation();
        Location merged = Location.builder()
                .city(patch.getCity() != null ? patch.getCity() : current == null ? null : current.getCity())
                .country(patch.getCountry() != null ? patch.getCountry() : current == null ? null : current.getCountry())
                .latitude(patch.getLatitude() != null ? patch.getLatitude() : current == null ? null : current.getLatitude())
                .longitude(patch.getLongitude() != null ? patch.getLongitude() : current == null ? null : current.getLongitude())
                .timezone(patch.getTimezone() != null ? patch.getTimezone() : current == null ? null : current.getTimezone())
                .method(patch.getMethod() != null ? patch.getMethod() : current == null ? null : current.getMethod())
                .build();
        validateLocation(merged);
        existing.setLocation(merged);
    }

    /**
     * Rejects a location the board could not use. Coordinates and timezone that are absent are
     * allowed (the column is nullable); present ones must be real, because a typo here shifts
     * every prayer time without any visible error.
     */
    private static void validateLocation(Location location) {
        Double lat = location.getLatitude();
        if (lat != null && (lat.isNaN() || lat < -90 || lat > 90)) {
            throw badRequest("latitude must be between -90 and 90");
        }
        Double lon = location.getLongitude();
        if (lon != null && (lon.isNaN() || lon < -180 || lon > 180)) {
            throw badRequest("longitude must be between -180 and 180");
        }
        String timezone = location.getTimezone();
        if (timezone != null) {
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException e) {
                throw badRequest("Unknown timezone: " + timezone);
            }
        }
    }

    private static ApiResponseException badRequest(String message) {
        return new ApiResponseException(HttpStatus.BAD_REQUEST, ErrorResponse.of(message));
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
                        .prayerTime(parsePrayerTime(slot.prayerTime()))
                        .khatib(slot.khatib())
                        .room(slot.room())
                        .build();
                content.getJummahPrayers().add(prayer);
            }
        }

        WeeklyContent saved = weeklyContentRepository.save(content);
        log.info("Saved weekly content for week {} of {}", weekNumber, year);
        contentChangedAfterCommit("weekly-content");
        return saved;
    }

    /** Parses a 24-hour {@code HH:mm} time; anything else is the client's mistake, not a 500. */
    private static LocalTime parsePrayerTime(String value) {
        if (value == null) return null;
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw badRequest("prayerTime must be a 24-hour HH:mm time, got: " + value);
        }
    }

    /**
     * Tells the boards to refetch once the surrounding transaction has committed. Notifying
     * inside it lets a board refetch before the commit and read the old content, then stay on
     * it until the next change. With no transaction active the save has already committed, so
     * it notifies immediately, as every other mutator here does.
     */
    private void contentChangedAfterCommit(String what) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    boardStream.contentChanged(what);
                }
            });
        } else {
            boardStream.contentChanged(what);
        }
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

    public List<BoardEvent> getEventsForAudienceInRange(Audience audience, Instant rangeStart, Instant rangeEnd) {
        return boardEventRepository.findOverlappingForAudienceOrBoth(audience, rangeStart, rangeEnd);
    }

    public BoardEvent createEvent(CreateCalendarEventRequest request) {
        Instant start = Instant.ofEpochMilli(request.getStartEpochMs());
        Instant end = Instant.ofEpochMilli(request.getEndEpochMs());
        validateEventTimes(start, end);
        BoardEvent boardEvent = BoardEvent.builder()
                .name(request.getName())
                .description(request.getDescription())
                .location(request.getLocation())
                .startTime(start)
                .endTime(end)
                // Optional in the contract. An explicit null would override the builder
                // default and hit the NOT NULL column, so null means false here.
                .allDay(Boolean.TRUE.equals(request.getAllDay()))
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
        // Checked on the merged result, so patching only one end is held to the other.
        validateEventTimes(existing.getStartTime(), existing.getEndTime());
        BoardEvent saved = boardEventRepository.save(existing);
        log.info("Updated event: id={}", eventId);
        boardStream.contentChanged("events");
        return saved;
    }

    private static void validateEventTimes(Instant start, Instant end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw badRequest("End time must not be before start time");
        }
    }

    public void deleteEvent(UUID eventId) {
        BoardEvent boardEvent = getEventById(eventId);
        boardEventRepository.delete(boardEvent);
        log.info("Deleted event: id={}", eventId);
        boardStream.contentChanged("events");
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
