package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.board.request.CreateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateBoardConfigRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdateCalendarEventRequest;
import com.ibrasoft.lensbridge.dto.board.request.WeeklyContentRequest;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
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

    @InjectMocks
    private BoardService service;

    private DeviceConfig config(UUID deviceId) {
        DeviceConfig c = new DeviceConfig();
        c.setId(deviceId);
        c.setPosterCycleIntervalMs(5000);
        return c;
    }

    // ==================== Board Config ====================

    @Test
    void getBoardConfigReturnsOptional() {
        UUID id = UUID.randomUUID();
        DeviceConfig c = config(id);
        when(boardConfigRepository.findById(id)).thenReturn(Optional.of(c));

        assertThat(service.getBoardConfig(id)).contains(c);
    }

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
                .posterCycleIntervalMs(9000)
                .enableScrollingMessage(true)
                .build();

        DeviceConfig saved = service.updateBoardConfig(id, request);

        assertThat(saved.getPosterCycleIntervalMs()).isEqualTo(9000);
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
    void getWeeklyContentDelegatesByYearAndWeek() {
        WeeklyContent wc = WeeklyContent.builder().year(2026).weekNumber(20).build();
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.of(wc));

        assertThat(service.getWeeklyContent(2026, 20)).contains(wc);
    }

    @Test
    void getWeeklyContentOrThrowThrowsWhenMissing() {
        when(weeklyContentRepository.findByYearAndWeekNumber(2026, 20)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWeeklyContentOrThrow(2026, 20))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getCurrentWeeklyContentResolvesViaWeekIdFromToday() {
        WeekId current = WeekId.fromDate(LocalDate.now());
        WeeklyContent wc = WeeklyContent.builder()
                .year(current.getYear()).weekNumber(current.getWeekNumber()).build();
        when(weeklyContentRepository.findByYearAndWeekNumber(
                current.getYear(), current.getWeekNumber())).thenReturn(Optional.of(wc));

        assertThat(service.getCurrentWeeklyContent()).contains(wc);
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
                        IslamicQuote.Kind.VERSE, "ar", "tr", "en", "Quran 1:1")))
                .jummahPrayers(List.of(new WeeklyContentRequest.JummahSlot("13:30", "Imam", "Hall")))
                .build();

        WeeklyContent saved = service.saveWeeklyContent(2026, 20, request);

        assertThat(saved.getYear()).isEqualTo(2026);
        assertThat(saved.getWeekNumber()).isEqualTo(20);
        assertThat(saved.getQuotes()).hasSize(1);
        assertThat(saved.getQuotes().get(0).getKind()).isEqualTo(IslamicQuote.Kind.VERSE);
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
                        IslamicQuote.Kind.HADITH, "a", "t", "e", "ref")))
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
    void getUpcomingEventsForAudienceDelegates() {
        List<BoardEvent> events = List.of(BoardEvent.builder().name("e").build());
        when(boardEventRepository.findUpcomingByAudienceOrBoth(eq(Audience.BROTHERS), any(Instant.class)))
                .thenReturn(events);

        assertThat(service.getUpcomingEventsForAudience(Audience.BROTHERS)).isEqualTo(events);
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

    // @Test
    // void createEventBuildsAndPersists() {
    //     when(boardEventRepository.save(any(BoardEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    //     Instant start = Instant.now();
    //     CreateCalendarEventRequest request = CreateCalendarEventRequest.builder()
    //             .name("Halaqa")
    //             .description("desc")
    //             .location("MSA Room")
    //             .startTime(start)
    //             .endTime(start.plusSeconds(3600))
    //             .allDay(false)
    //             .audience(Audience.BOTH)
    //             .build();

    //     BoardEvent created = service.createEvent(request);

    //     assertThat(created.getName()).isEqualTo("Halaqa");
    //     assertThat(created.getLocation()).isEqualTo("MSA Room");
    //     assertThat(created.getAudience()).isEqualTo(Audience.BOTH);
    // }

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

    @Test
    void updateBoardConfigCapturesPatchedLocation() {
        UUID id = UUID.randomUUID();
        DeviceConfig existing = config(id);
        when(boardConfigRepository.findById(id)).thenReturn(Optional.of(existing));
        when(boardConfigRepository.save(any(DeviceConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Location loc = Location.builder().city("Toronto").timezone("America/Toronto").build();
        service.updateBoardConfig(id, UpdateBoardConfigRequest.builder().location(loc).build());

        ArgumentCaptor<DeviceConfig> captor = ArgumentCaptor.forClass(DeviceConfig.class);
        verify(boardConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getLocation()).isSameAs(loc);
    }
}
