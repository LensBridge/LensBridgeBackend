package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.board.request.CreateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateDeviceRequest;
import com.ibrasoft.lensbridge.dto.board.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.IslamicQuote;
import com.ibrasoft.lensbridge.model.board.Location;
import com.ibrasoft.lensbridge.model.board.WeekId;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.repository.sql.BoardConfigRepository;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.repository.sql.WeeklyContentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardConfigRepository boardConfigRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private BoardEventRepository boardEventRepository;
    @Mock
    private WeeklyContentRepository weeklyContentRepository;
    @Mock
    private BoardStreamHandler boardStream;

    @InjectMocks
    private BoardService service;

    private DeviceConfig config(UUID deviceId) {
        DeviceConfig c = new DeviceConfig();
        c.setId(deviceId);
        c.setDarkModeAfterIsha(true);
        return c;
    }

    // ==================== Board Config ====================

    @Test
    void getBoardConfigOrThrowThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(boardConfigRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBoardConfigOrThrow(id))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void saveBoardConfigAttachesDeviceAndPersists() {
        UUID id = UUID.randomUUID();
        Device device = Device.builder().id(id).displayName("d").build();
        DeviceConfig c = config(id);
        when(deviceRepository.findById(id)).thenReturn(Optional.of(device));
        when(boardConfigRepository.save(c)).thenReturn(c);

        DeviceConfig saved = service.saveBoardConfig(id, c);

        assertThat(saved.getDevice()).isSameAs(device);
        verify(boardConfigRepository).save(c);
    }

    @Test
    void saveBoardConfigThrowsWhenDeviceMissing() {
        UUID id = UUID.randomUUID();
        when(deviceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveBoardConfig(id, config(id)))
                .isInstanceOf(ApiResponseException.class);
        verify(boardConfigRepository, never()).save(any());
    }

    @Test
    void updateBoardConfigPatchesOnlyNonNullFields() {
        UUID id = UUID.randomUUID();
        DeviceConfig existing = config(id);
        existing.setEnableScrollingMessage(false);
        when(boardConfigRepository.findById(id)).thenReturn(Optional.of(existing));
        when(boardConfigRepository.save(existing)).thenReturn(existing);

        UpdateBoardConfigRequest request = UpdateBoardConfigRequest.builder()
                .enableScrollingMessage(true)
                .build();

        DeviceConfig saved = service.updateBoardConfig(id, request);

        assertThat(saved.isEnableScrollingMessage()).isTrue();
        assertThat(saved.getLocation()).isNull(); // untouched
    }

    @Test
    void updateBoardConfigThrowsWhenConfigMissing() {
        UUID id = UUID.randomUUID();
        when(boardConfigRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBoardConfig(id, UpdateBoardConfigRequest.builder().build()))
                .isInstanceOf(ApiResponseException.class);
    }

    @Test
    void getAllBoardConfigsDelegates() {
        List<DeviceConfig> all = List.of(config(UUID.randomUUID()));
        when(boardConfigRepository.findAll()).thenReturn(all);

        assertThat(service.getAllBoardConfigs()).isEqualTo(all);
    }

    // ==================== Weekly Content ====================

    @Test
    void getWeeklyContentOrThrowThrowsWhenMissing() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWeeklyContentOrThrow(2026, 20))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getWeeklyContentByYearDelegates() {
        List<WeeklyContent> list = List.of(WeeklyContent.builder().year(2026).weekNumber(1).build());
        when(weeklyContentRepository.findByYear(2026)).thenReturn(list);

        assertThat(service.getWeeklyContentByYear(2026)).isEqualTo(list);
    }

    @Test
    void saveWeeklyContentCreatesNewWhenAbsentAndMapsQuotes() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());
        when(weeklyContentRepository.save(any(WeeklyContent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        WeeklyContentRequest request = WeeklyContentRequest.builder()
                .quotes(List.of(new WeeklyContentRequest.QuoteEntry(
                        IslamicQuote.Kind.VERSE, "ar", "tr", "en", "Quran 1:1", 30)))
                .jummahPrayers(List.of(new WeeklyContentRequest.JummahSlot("13:30", "Imam", "Hall")))
                .build();

        WeeklyContent saved = service.saveWeeklyContent(2026, 20, request);

        assertThat(saved.getYear()).isEqualTo(2026);
        assertThat(saved.getWeekNumber()).isEqualTo(20);
        assertThat(saved.getQuotes()).hasSize(1);
        assertThat(saved.getQuotes().get(0).getKind()).isEqualTo(IslamicQuote.Kind.VERSE);
        assertThat(saved.getQuotes().get(0).getDurationSeconds()).isEqualTo(30);
        assertThat(saved.getJummahPrayers()).hasSize(1);
        assertThat(saved.getJummahPrayers().get(0).getKhatib()).isEqualTo("Imam");
        assertThat(saved.getJummahPrayers().get(0).getPrayerTime().toString()).isEqualTo("13:30");
    }

    @Test
    void saveWeeklyContentReusesExistingAndClearsQuotesBeforeRepopulating() {
        WeeklyContent existing = WeeklyContent.builder().year(2026).weekNumber(20).build();
        existing.getQuotes().add(IslamicQuote.builder().arabic("stale").build());
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20))
                .thenReturn(Optional.of(existing));
        when(weeklyContentRepository.save(any(WeeklyContent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        WeeklyContentRequest request = WeeklyContentRequest.builder()
                .quotes(List.of(new WeeklyContentRequest.QuoteEntry(
                        IslamicQuote.Kind.HADITH, "a", "t", "e", "ref", null)))
                .build();

        WeeklyContent saved = service.saveWeeklyContent(2026, 20, request);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getQuotes()).hasSize(1);
        assertThat(saved.getQuotes().get(0).getArabic()).isEqualTo("a");
    }

    @Test
    void saveWeeklyContentLeavesCollectionsUntouchedWhenRequestFieldsNull() {
        WeeklyContent existing = WeeklyContent.builder().year(2026).weekNumber(20).build();
        existing.getQuotes().add(IslamicQuote.builder().arabic("keep").build());
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20))
                .thenReturn(Optional.of(existing));
        when(weeklyContentRepository.save(any(WeeklyContent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        WeeklyContent saved = service.saveWeeklyContent(2026, 20,
                WeeklyContentRequest.builder().build());

        assertThat(saved.getQuotes()).hasSize(1);
        assertThat(saved.getQuotes().get(0).getArabic()).isEqualTo("keep");
    }

    @Test
    void deleteWeeklyContentThrowsWhenMissing() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteWeeklyContent(2026, 20))
                .isInstanceOf(ApiResponseException.class);
        verify(weeklyContentRepository, never()).delete(any());
    }

    @Test
    void deleteWeeklyContentDeletesWhenPresent() {
        WeeklyContent wc = WeeklyContent.builder().year(2026).weekNumber(20).build();
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20))
                .thenReturn(Optional.of(wc));

        service.deleteWeeklyContent(2026, 20);

        verify(weeklyContentRepository).delete(wc);
    }

    // ==================== Events ====================

    @Test
    void getAllEventsDelegatesSortedByStartTime() {
        List<BoardEvent> events = List.of(BoardEvent.builder().name("e").build());
        when(boardEventRepository.findAllByOrderByStartTimeAsc()).thenReturn(events);

        assertThat(service.getAllEvents()).isEqualTo(events);
    }

    @Test
    void getEventByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(boardEventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventById(id))
                .isInstanceOf(ApiResponseException.class);
    }

    @Test
    void getEventsForAudienceFiltersByAudienceOrBoth() {
        List<BoardEvent> events = List.of(BoardEvent.builder().name("e").build());
        when(boardEventRepository.findByAudienceOrBoth(Audience.SISTERS)).thenReturn(events);

        assertThat(service.getEventsForAudience(Audience.SISTERS)).isEqualTo(events);
    }

    @Test
    void getEventsForAudienceInRangeDelegatesWithRange() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        List<BoardEvent> events = List.of(BoardEvent.builder().name("e").build());
        when(boardEventRepository.findOverlappingForAudienceOrBoth(Audience.BOTH, start, end))
                .thenReturn(events);

        assertThat(service.getEventsForAudienceInRange(Audience.BOTH, start, end)).isEqualTo(events);
    }

    private CreateCalendarEventRequest.CreateCalendarEventRequestBuilder eventRequest(Instant start) {
        return CreateCalendarEventRequest.builder()
                .name("Halaqa")
                .description("desc")
                .location("MSA Room")
                .startEpochMs(start.toEpochMilli())
                .endEpochMs(start.plusSeconds(3600).toEpochMilli())
                .allDay(false)
                .audience(Audience.BOTH);
    }

    @Test
    void createEventBuildsAndPersists() {
        when(boardEventRepository.save(any(BoardEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        Instant start = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        BoardEvent created = service.createEvent(eventRequest(start).build());

        assertThat(created.getName()).isEqualTo("Halaqa");
        assertThat(created.getLocation()).isEqualTo("MSA Room");
        assertThat(created.getAudience()).isEqualTo(Audience.BOTH);
        assertThat(created.getStartTime()).isEqualTo(start);
        assertThat(created.getEndTime()).isEqualTo(start.plusSeconds(3600));
        verify(boardStream).contentChanged("events");
    }

    /** allDay is optional in the contract; null must not reach the NOT NULL column. */
    @Test
    void createEventTreatsAMissingAllDayAsFalse() {
        when(boardEventRepository.save(any(BoardEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardEvent created = service.createEvent(eventRequest(Instant.now()).allDay(null).build());

        assertThat(created.getAllDay()).isFalse();
    }

    @Test
    void createEventKeepsAnExplicitAllDayTrue() {
        when(boardEventRepository.save(any(BoardEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardEvent created = service.createEvent(eventRequest(Instant.now()).allDay(true).build());

        assertThat(created.getAllDay()).isTrue();
    }

    @Test
    void createEventRejectsAnEndBeforeTheStart() {
        Instant start = Instant.now();
        CreateCalendarEventRequest request = eventRequest(start)
                .endEpochMs(start.minusSeconds(1).toEpochMilli())
                .build();

        assertThatThrownBy(() -> service.createEvent(request))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(boardEventRepository, never()).save(any());
    }

    @Test
    void updateEventRejectsAnEndBeforeTheStoredStart() {
        UUID id = UUID.randomUUID();
        Instant start = Instant.now();
        BoardEvent existing = BoardEvent.builder().id(id).name("e")
                .startTime(start).endTime(start.plusSeconds(3600)).audience(Audience.BOTH).build();
        when(boardEventRepository.findById(id)).thenReturn(Optional.of(existing));

        // Only the end is patched: it is still held against the stored start.
        UpdateCalendarEventRequest request = UpdateCalendarEventRequest.builder()
                .endTime(start.minusSeconds(60)).build();

        assertThatThrownBy(() -> service.updateEvent(id, request))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(boardEventRepository, never()).save(any());
    }

    @Test
    void updateEventRejectsAStartAfterTheStoredEnd() {
        UUID id = UUID.randomUUID();
        Instant start = Instant.now();
        BoardEvent existing = BoardEvent.builder().id(id).name("e")
                .startTime(start).endTime(start.plusSeconds(3600)).audience(Audience.BOTH).build();
        when(boardEventRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateCalendarEventRequest request = UpdateCalendarEventRequest.builder()
                .startTime(start.plusSeconds(7200)).build();

        assertThatThrownBy(() -> service.updateEvent(id, request))
                .isInstanceOf(ApiResponseException.class);
        verify(boardEventRepository, never()).save(any());
    }

    @Test
    void updateEventPatchesOnlyNonNullFields() {
        UUID id = UUID.randomUUID();
        BoardEvent existing = BoardEvent.builder()
                .id(id).name("Old").description("d").audience(Audience.BOTH).build();
        when(boardEventRepository.findById(id)).thenReturn(Optional.of(existing));
        when(boardEventRepository.save(any(BoardEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCalendarEventRequest request = UpdateCalendarEventRequest.builder()
                .name("New").build();

        BoardEvent updated = service.updateEvent(id, request);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDescription()).isEqualTo("d"); // unchanged
        assertThat(updated.getAudience()).isEqualTo(Audience.BOTH); // unchanged
    }

    @Test
    void deleteEventThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(boardEventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteEvent(id))
                .isInstanceOf(ApiResponseException.class);
        verify(boardEventRepository, never()).delete(any());
    }

    @Test
    void deleteEventDeletesWhenPresent() {
        UUID id = UUID.randomUUID();
        BoardEvent existing = BoardEvent.builder().id(id).name("e").build();
        when(boardEventRepository.findById(id)).thenReturn(Optional.of(existing));

        service.deleteEvent(id);

        verify(boardEventRepository).delete(existing);
    }

    // ==================== location merge ====================

    private DeviceConfig configWithLocation(UUID id) {
        DeviceConfig existing = config(id);
        existing.setLocation(Location.builder()
                .city("Mississauga").country("CA")
                .latitude(43.589).longitude(-79.644)
                .timezone("America/Toronto").method(com.ibrasoft.lensbridge.model.board.CalculationMethod.ISNA)
                .build());
        when(boardConfigRepository.findById(id)).thenReturn(Optional.of(existing));
        // Lenient: the rejection tests never reach the save.
        org.mockito.Mockito.lenient().when(boardConfigRepository.save(any(DeviceConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return existing;
    }

    private UpdateBoardConfigRequest locationPatch(Location location) {
        return UpdateBoardConfigRequest.builder().location(location).build();
    }

    @Test
    void updateBoardConfigMergesALocationPatchFieldByField() {
        UUID id = UUID.randomUUID();
        configWithLocation(id);

        DeviceConfig saved = service.updateBoardConfig(id,
                locationPatch(Location.builder().city("Toronto").build()));

        assertThat(saved.getLocation().getCity()).isEqualTo("Toronto");
        assertThat(saved.getLocation().getCountry()).isEqualTo("CA");
        assertThat(saved.getLocation().getTimezone()).isEqualTo("America/Toronto");
        assertThat(saved.getLocation().getMethod())
                .isEqualTo(com.ibrasoft.lensbridge.model.board.CalculationMethod.ISNA);
    }

    /** The regression: replacing the embeddable zeroed the primitive coordinates. */
    @Test
    void aTimezoneOnlyPatchLeavesTheCoordinatesAlone() {
        UUID id = UUID.randomUUID();
        configWithLocation(id);

        DeviceConfig saved = service.updateBoardConfig(id,
                locationPatch(Location.builder().timezone("America/Vancouver").build()));

        assertThat(saved.getLocation().getTimezone()).isEqualTo("America/Vancouver");
        assertThat(saved.getLocation().getLatitude()).isEqualTo(43.589);
        assertThat(saved.getLocation().getLongitude()).isEqualTo(-79.644);
    }

    @Test
    void aCoordinatePatchOverwritesOnlyThatCoordinate() {
        UUID id = UUID.randomUUID();
        configWithLocation(id);

        DeviceConfig saved = service.updateBoardConfig(id,
                locationPatch(Location.builder().latitude(0.0).build()));

        // An explicit 0.0 is a real value, distinct from "absent".
        assertThat(saved.getLocation().getLatitude()).isEqualTo(0.0);
        assertThat(saved.getLocation().getLongitude()).isEqualTo(-79.644);
    }

    @Test
    void aLocationPatchOnAConfigWithoutOneCreatesIt() {
        UUID id = UUID.randomUUID();
        DeviceConfig existing = config(id);
        when(boardConfigRepository.findById(id)).thenReturn(Optional.of(existing));
        when(boardConfigRepository.save(any(DeviceConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DeviceConfig saved = service.updateBoardConfig(id,
                locationPatch(Location.builder().city("Toronto").timezone("America/Toronto").build()));

        assertThat(saved.getLocation().getCity()).isEqualTo("Toronto");
        assertThat(saved.getLocation().getLatitude()).isNull();
    }

    private void assertLocationRejected(Location bad) {
        UUID id = UUID.randomUUID();
        DeviceConfig existing = configWithLocation(id);

        assertThatThrownBy(() -> service.updateBoardConfig(id, locationPatch(bad)))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(boardConfigRepository, never()).save(any());
        // Rejected before anything is applied.
        assertThat(existing.getLocation().getTimezone()).isEqualTo("America/Toronto");
        assertThat(existing.getLocation().getLatitude()).isEqualTo(43.589);
    }

    @Test
    void anUnknownTimezoneIsRejected() {
        assertLocationRejected(Location.builder().timezone("Mars/Olympus").build());
    }

    @Test
    void aLatitudeOutOfRangeIsRejected() {
        assertLocationRejected(Location.builder().latitude(90.5).build());
        assertLocationRejected(Location.builder().latitude(-91.0).build());
    }

    @Test
    void aLongitudeOutOfRangeIsRejected() {
        assertLocationRejected(Location.builder().longitude(180.5).build());
        assertLocationRejected(Location.builder().longitude(-181.0).build());
    }

    @Test
    void theBoundaryCoordinatesAreAccepted() {
        UUID id = UUID.randomUUID();
        configWithLocation(id);

        DeviceConfig saved = service.updateBoardConfig(id,
                locationPatch(Location.builder().latitude(-90.0).longitude(180.0).build()));

        assertThat(saved.getLocation().getLatitude()).isEqualTo(-90.0);
        assertThat(saved.getLocation().getLongitude()).isEqualTo(180.0);
    }

    // ==================== saveBoardConfig (PUT) ====================

    @Test
    void saveBoardConfigForcesTheIdToThePathDeviceWhenTheBodyHasNone() {
        UUID id = UUID.randomUUID();
        Device device = Device.builder().id(id).displayName("d").build();
        DeviceConfig body = new DeviceConfig(); // no id
        when(deviceRepository.findById(id)).thenReturn(Optional.of(device));
        when(boardConfigRepository.save(any(DeviceConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceConfig saved = service.saveBoardConfig(id, body);

        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getDevice()).isSameAs(device);
    }

    /** A body naming another device must not overwrite that device's row. */
    @Test
    void saveBoardConfigIgnoresAnIdInTheBodyThatIsNotThePathDevice() {
        UUID id = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Device device = Device.builder().id(id).displayName("d").build();
        DeviceConfig body = config(other);
        when(deviceRepository.findById(id)).thenReturn(Optional.of(device));
        when(boardConfigRepository.save(any(DeviceConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveBoardConfig(id, body);

        ArgumentCaptor<DeviceConfig> captor = ArgumentCaptor.forClass(DeviceConfig.class);
        verify(boardConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
    }

    @Test
    void saveBoardConfigRejectsALocationWithoutCoordinates() {
        UUID id = UUID.randomUUID();
        Device device = Device.builder().id(id).displayName("d").build();
        DeviceConfig body = config(id);
        body.setLocation(Location.builder().city("Toronto").timezone("America/Toronto").build());
        when(deviceRepository.findById(id)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> service.saveBoardConfig(id, body))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(boardConfigRepository, never()).save(any());
    }

    @Test
    void saveBoardConfigRejectsAnUnknownTimezone() {
        UUID id = UUID.randomUUID();
        Device device = Device.builder().id(id).displayName("d").build();
        DeviceConfig body = config(id);
        body.setLocation(Location.builder().latitude(1.0).longitude(2.0).timezone("Nowhere/Land").build());
        when(deviceRepository.findById(id)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> service.saveBoardConfig(id, body))
                .isInstanceOf(ApiResponseException.class);
        verify(boardConfigRepository, never()).save(any());
    }

    // ==================== weekly content: times and notification ====================

    @Test
    void saveWeeklyContentRejectsAMalformedJummahTime() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());

        WeeklyContentRequest request = WeeklyContentRequest.builder()
                .jummahPrayers(List.of(new WeeklyContentRequest.JummahSlot("1:30pm", "Imam", "Hall")))
                .build();

        assertThatThrownBy(() -> service.saveWeeklyContent(2026, 20, request))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(weeklyContentRepository, never()).save(any());
    }

    @Test
    void saveWeeklyContentRejectsAnOutOfRangeJummahTime() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());

        WeeklyContentRequest request = WeeklyContentRequest.builder()
                .jummahPrayers(List.of(new WeeklyContentRequest.JummahSlot("25:99", null, null)))
                .build();

        assertThatThrownBy(() -> service.saveWeeklyContent(2026, 20, request))
                .isInstanceOf(ApiResponseException.class);
    }

    @Test
    void saveWeeklyContentAcceptsANullJummahTime() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());
        when(weeklyContentRepository.save(any(WeeklyContent.class))).thenAnswer(inv -> inv.getArgument(0));

        WeeklyContent saved = service.saveWeeklyContent(2026, 20, WeeklyContentRequest.builder()
                .jummahPrayers(List.of(new WeeklyContentRequest.JummahSlot(null, "Imam", "Hall")))
                .build());

        assertThat(saved.getJummahPrayers().get(0).getPrayerTime()).isNull();
    }

    @Test
    void saveWeeklyContentNotifiesImmediatelyWhenThereIsNoTransaction() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());
        when(weeklyContentRepository.save(any(WeeklyContent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveWeeklyContent(2026, 20, WeeklyContentRequest.builder().build());

        verify(boardStream).contentChanged("weekly-content");
    }

    /** Notifying before the commit let a board refetch and read the old content. */
    @Test
    void saveWeeklyContentNotifiesOnlyAfterTheTransactionCommits() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());
        when(weeklyContentRepository.save(any(WeeklyContent.class))).thenAnswer(inv -> inv.getArgument(0));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.saveWeeklyContent(2026, 20, WeeklyContentRequest.builder().build());

            verify(boardStream, never()).contentChanged(any());

            for (var sync : org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations()) {
                sync.afterCommit();
            }
            verify(boardStream).contentChanged("weekly-content");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ==================== slide durations ====================

    private DeviceConfig configForDurationTest(UUID id) {
        DeviceConfig existing = config(id);
        when(boardConfigRepository.findById(id)).thenReturn(Optional.of(existing));
        when(boardConfigRepository.save(any(DeviceConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return existing;
    }

    @Test
    void updateBoardConfigPinsTheAgendaDuration() {
        UUID id = UUID.randomUUID();
        configForDurationTest(id);

        DeviceConfig saved = service.updateBoardConfig(
                id, UpdateBoardConfigRequest.builder().agendaDurationSeconds(35).build());

        assertThat(saved.getAgendaDurationSeconds()).isEqualTo(35);
    }

    /** Zero is the only way to say "clear it" — Patch skips null, so null means "leave it". */
    @Test
    void updateBoardConfigTreatsZeroAgendaDurationAsAuto() {
        UUID id = UUID.randomUUID();
        DeviceConfig existing = configForDurationTest(id);
        existing.setAgendaDurationSeconds(35);

        DeviceConfig saved = service.updateBoardConfig(
                id, UpdateBoardConfigRequest.builder().agendaDurationSeconds(0).build());

        assertThat(saved.getAgendaDurationSeconds()).isNull();
    }

    @Test
    void updateBoardConfigLeavesAgendaDurationAloneWhenOmitted() {
        UUID id = UUID.randomUUID();
        DeviceConfig existing = configForDurationTest(id);
        existing.setAgendaDurationSeconds(35);

        DeviceConfig saved = service.updateBoardConfig(
                id, UpdateBoardConfigRequest.builder().darkModeAfterIsha(true).build());

        assertThat(saved.getAgendaDurationSeconds()).isEqualTo(35);
    }

    /** The 1–4 gap the DTO's @Min(0) cannot express. */
    @Test
    void updateBoardConfigRejectsAnAgendaDurationBetweenZeroAndTheMinimum() {
        UUID id = UUID.randomUUID();
        when(boardConfigRepository.findById(id)).thenReturn(Optional.of(config(id)));

        assertThatThrownBy(() -> service.updateBoardConfig(
                id, UpdateBoardConfigRequest.builder().agendaDurationSeconds(3).build()))
                .isInstanceOf(ApiResponseException.class);

        verify(boardConfigRepository, never()).save(any(DeviceConfig.class));
    }

    @Test
    void updateBoardConfigPinsTheNextPrayerDuration() {
        UUID id = UUID.randomUUID();
        configForDurationTest(id);

        DeviceConfig saved = service.updateBoardConfig(
                id, UpdateBoardConfigRequest.builder().nextPrayerDurationSeconds(25).build());

        assertThat(saved.getNextPrayerDurationSeconds()).isEqualTo(25);
    }

    /** Unset means the default, never null — the payload always carries a concrete number. */
    @Test
    void nextPrayerDurationDefaultsWhenNothingIsStored() {
        assertThat(DeviceConfig.builder().build().getNextPrayerDurationSeconds())
                .isEqualTo(DeviceConfig.DEFAULT_NEXT_PRAYER_DURATION_SECONDS);
    }

    // ==================== updateDevice ====================

    /**
     * Validation rejects a name that is <em>only</em> whitespace; this covers the padded
     * one it lets through. " Musallah A " and "Musallah A" are the same board to anyone
     * reading the admin list, and only one of them sorts where you expect.
     */
    @Test
    void updateDeviceTrimsTheDisplayName() {
        UUID id = UUID.randomUUID();
        Device existing = Device.builder().id(id).displayName("Old").audience(Audience.BOTH).build();
        when(deviceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device updated = service.updateDevice(
                id, UpdateDeviceRequest.builder().displayName("  Musallah A  ").build());

        assertThat(updated.getDisplayName()).isEqualTo("Musallah A");
    }

    /** A PATCH omitting a field must leave it alone rather than nulling it. */
    @Test
    void updateDeviceLeavesOmittedFieldsUntouched() {
        UUID id = UUID.randomUUID();
        Device existing = Device.builder().id(id).displayName("Musallah A")
                .audience(Audience.BROTHERS).build();
        when(deviceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device updated = service.updateDevice(
                id, UpdateDeviceRequest.builder().audience(Audience.SISTERS).build());

        assertThat(updated.getDisplayName()).isEqualTo("Musallah A");
        assertThat(updated.getAudience()).isEqualTo(Audience.SISTERS);
    }

    @Test
    void updateDeviceRejectsAnUnknownDevice() {
        UUID id = UUID.randomUUID();
        when(deviceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDevice(
                id, UpdateDeviceRequest.builder().displayName("X").build()))
                .isInstanceOf(ApiResponseException.class);

        verify(deviceRepository, never()).save(any(Device.class));
    }
}
