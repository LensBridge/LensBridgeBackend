package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.dto.board.response.frames.DayBucket;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.model.board.frames.AgendaFrameConfig;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendaFrameProducerTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    @Mock
    private BoardService boardService;

    @InjectMocks
    private AgendaFrameProducer producer;

    /** Wednesday 2026-05-13, 10:00 local. */
    private BoardContext ctx() {
        return BoardContext.builder()
                .device(Device.builder().audience(Audience.BOTH).build())
                .now(ZonedDateTime.of(2026, 5, 13, 10, 0, 0, 0, TORONTO))
                .build();
    }

    /** Local wall-clock times in Toronto, so tests read the way the board does. */
    private Instant local(String isoLocalDateTime) {
        return java.time.LocalDateTime.parse(isoLocalDateTime).atZone(TORONTO).toInstant();
    }

    private BoardEvent event(String name, String start, String end) {
        return BoardEvent.builder()
                .name(name)
                .startTime(local(start))
                .endTime(local(end))
                .allDay(false)
                .audience(Audience.BOTH)
                .build();
    }

    private AgendaFrameConfig configFrom(List<BoardEvent> events) {
        when(boardService.getEventsForAudienceInRange(any(), any(), any())).thenReturn(events);
        FrameDefinition def = producer.produce(ctx()).get(0);
        return (AgendaFrameConfig) def.getFrameConfig();
    }

    private DayBucket bucketFor(AgendaFrameConfig config, String isoDate) {
        return config.getDays().stream()
                .filter(b -> b.getDate().equals(LocalDate.parse(isoDate)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no bucket for " + isoDate));
    }

    @Test
    void producesOneAgendaFrameWithExpectedShell() {
        when(boardService.getEventsForAudienceInRange(any(), any(), any()))
                .thenReturn(List.of(event("A", "2026-05-13T18:00", "2026-05-13T19:00")));

        List<FrameDefinition> frames = producer.produce(ctx());

        assertThat(frames).hasSize(1);
        FrameDefinition def = frames.get(0);
        assertThat(def.getFrameId()).isEqualTo("agenda");
        assertThat(def.getFrameType()).isEqualTo(FrameType.AGENDA);
        assertThat(def.getFrameConfig()).isInstanceOf(AgendaFrameConfig.class);
    }

    @Test
    void queriesTheDeviceAudienceOverTheRollingWindow() {
        when(boardService.getEventsForAudienceInRange(
                eq(Audience.BOTH),
                eq(local("2026-05-13T00:00")),
                any()))
                .thenReturn(List.of(event("A", "2026-05-13T18:00", "2026-05-13T19:00")));

        assertThat(producer.produce(ctx())).hasSize(1);
    }

    @Test
    void emitsSevenConsecutiveDaysStartingToday() {
        AgendaFrameConfig config =
                configFrom(List.of(event("A", "2026-05-13T18:00", "2026-05-13T19:00")));

        assertThat(config.getDays()).hasSize(7);
        assertThat(config.getDays().get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 13));
        assertThat(config.getDays().get(6).getDate()).isEqualTo(LocalDate.of(2026, 5, 19));
    }

    @Test
    void daysWithNoEventsStillGetAnEmptyBucket() {
        AgendaFrameConfig config =
                configFrom(List.of(event("A", "2026-05-13T18:00", "2026-05-13T19:00")));

        assertThat(bucketFor(config, "2026-05-15").getEvents()).isEmpty();
        assertThat(bucketFor(config, "2026-05-13").getEvents()).hasSize(1);
    }

    @Test
    void multiDayEventAppearsInEveryDayItOverlaps() {
        AgendaFrameConfig config =
                configFrom(List.of(event("Conference", "2026-05-14T09:00", "2026-05-16T17:00")));

        assertThat(bucketFor(config, "2026-05-13").getEvents()).isEmpty();
        assertThat(bucketFor(config, "2026-05-14").getEvents()).extracting("name")
                .containsExactly("Conference");
        assertThat(bucketFor(config, "2026-05-15").getEvents()).extracting("name")
                .containsExactly("Conference");
        assertThat(bucketFor(config, "2026-05-16").getEvents()).extracting("name")
                .containsExactly("Conference");
        assertThat(bucketFor(config, "2026-05-17").getEvents()).isEmpty();
    }

    @Test
    void eventEndingExactlyAtMidnightBelongsToTheDayItEnded() {
        AgendaFrameConfig config =
                configFrom(List.of(event("Late", "2026-05-14T22:00", "2026-05-15T00:00")));

        assertThat(bucketFor(config, "2026-05-14").getEvents()).hasSize(1);
        assertThat(bucketFor(config, "2026-05-15").getEvents()).isEmpty();
    }

    @Test
    void zeroLengthEventLandsOnItsStartDay() {
        AgendaFrameConfig config =
                configFrom(List.of(event("Instant", "2026-05-14T13:00", "2026-05-14T13:00")));

        assertThat(bucketFor(config, "2026-05-14").getEvents()).extracting("name")
                .containsExactly("Instant");
    }

    @Test
    void eventsWithinADayAreOrderedByStartTime() {
        AgendaFrameConfig config = configFrom(List.of(
                event("Evening", "2026-05-14T19:00", "2026-05-14T20:00"),
                event("Morning", "2026-05-14T08:00", "2026-05-14T09:00"),
                event("Noon", "2026-05-14T12:00", "2026-05-14T13:00")));

        assertThat(bucketFor(config, "2026-05-14").getEvents()).extracting("name")
                .containsExactly("Morning", "Noon", "Evening");
    }

    @Test
    void bucketsUseTheDeviceTimezoneNotUtc() {
        // 01:30 UTC on the 15th is still 21:30 on the 14th in Toronto.
        AgendaFrameConfig config = configFrom(List.of(BoardEvent.builder()
                .name("LateNight")
                .startTime(Instant.parse("2026-05-15T01:30:00Z"))
                .endTime(Instant.parse("2026-05-15T02:30:00Z"))
                .allDay(false)
                .audience(Audience.BOTH)
                .build()));

        assertThat(bucketFor(config, "2026-05-14").getEvents()).hasSize(1);
        assertThat(bucketFor(config, "2026-05-15").getEvents()).isEmpty();
    }

    /** null is the wire signal for "auto" — the board sizes the slide to what it renders. */
    @Test
    void durationIsNullWhenTheDeviceHasNoAgendaDurationSet() {
        when(boardService.getEventsForAudienceInRange(any(), any(), any()))
                .thenReturn(List.of(event("A", "2026-05-13T18:00", "2026-05-13T19:00")));

        assertThat(producer.produce(ctx()).get(0).getDurationInSeconds()).isNull();
    }

    @Test
    void durationIsTheConfiguredValueWhenTheDevicePinsOne() {
        when(boardService.getEventsForAudienceInRange(any(), any(), any()))
                .thenReturn(List.of(event("A", "2026-05-13T18:00", "2026-05-13T19:00")));

        BoardContext pinned = BoardContext.builder()
                .device(Device.builder().audience(Audience.BOTH).build())
                .config(DeviceConfig.builder().agendaDurationSeconds(35).build())
                .now(ZonedDateTime.of(2026, 5, 13, 10, 0, 0, 0, TORONTO))
                .build();

        assertThat(producer.produce(pinned).get(0).getDurationInSeconds()).isEqualTo(35);
    }

    /**
     * An empty window still emits the frame. The board needs a slide that says "nothing scheduled"
     * rather than silently dropping the agenda out of the rotation, so the shell is always present
     * and every day in the window gets its (empty) bucket.
     */
    @Test
    void emitsTheFrameWithEmptyBucketsWhenTheWholeWindowIsEmpty() {
        when(boardService.getEventsForAudienceInRange(any(), any(), any())).thenReturn(List.of());

        List<FrameDefinition> frames = producer.produce(ctx());

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).getFrameType()).isEqualTo(FrameType.AGENDA);

        AgendaFrameConfig config = (AgendaFrameConfig) frames.get(0).getFrameConfig();
        assertThat(config.getDays()).hasSize(7);
        assertThat(config.getDays()).allSatisfy(day -> assertThat(day.getEvents()).isEmpty());
        assertThat(config.getDays().get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 13));
        assertThat(config.getDays().get(6).getDate()).isEqualTo(LocalDate.of(2026, 5, 19));
    }
}
