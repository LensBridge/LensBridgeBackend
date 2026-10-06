package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.upload.EventStatus;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.repository.upload.EventsRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Status is derived from the event date and the clock; the stored column is only a cache that
 * the scheduled refresh keeps roughly current. Every test stores a deliberately stale status.
 */
class EventsServiceTest {

    private static final Instant EVENING = Instant.parse("2026-10-06T19:00:00Z");

    private final EventsRepository repository = mock(EventsRepository.class);

    private EventsService serviceAt(String now, int cutoffDays) {
        return new EventsService(repository, cutoffDays,
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private MediaEvent event(Instant date, EventStatus storedStatus) {
        MediaEvent event = MediaEvent.builder().id(UUID.randomUUID()).name("Iftar").date(date)
                .status(storedStatus).build();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        return event;
    }

    // ── isEventAcceptingUploads ───────────────────────────────────────────────

    @Test
    void anEveningEventIsClosedUntilItStartsEvenOnItsOwnDay() {
        MediaEvent event = event(EVENING, EventStatus.UPCOMING);

        assertThat(serviceAt("2026-10-05T23:59:00Z", 7).isEventAcceptingUploads(event.getId())).isFalse();
        assertThat(serviceAt("2026-10-06T08:00:00Z", 7).isEventAcceptingUploads(event.getId())).isFalse();
    }

    @Test
    void anEveningEventAcceptsUploadsWhileItIsOngoingDespiteAStaleStoredStatus() {
        // the old nightly job left this row UPCOMING all evening, so uploads were refused mid-event
        MediaEvent event = event(EVENING, EventStatus.UPCOMING);

        assertThat(serviceAt("2026-10-06T19:00:00Z", 7).isEventAcceptingUploads(event.getId())).isTrue();
        assertThat(serviceAt("2026-10-06T22:30:00Z", 7).isEventAcceptingUploads(event.getId())).isTrue();
    }

    @Test
    void aFinishedEventKeepsAcceptingUploadsForTheConfiguredCutoffThenStops() {
        MediaEvent event = event(EVENING, EventStatus.ONGOING);

        assertThat(serviceAt("2026-10-07T00:30:00Z", 2).isEventAcceptingUploads(event.getId())).isTrue();
        assertThat(serviceAt("2026-10-08T18:00:00Z", 2).isEventAcceptingUploads(event.getId())).isTrue();
        assertThat(serviceAt("2026-10-08T19:00:01Z", 2).isEventAcceptingUploads(event.getId())).isFalse();
    }

    @Test
    void theCutoffIsTheConfiguredOneNotAHardCodedSevenDays() {
        MediaEvent event = event(EVENING, EventStatus.PAST);

        // five days on: closed with a 3-day window, still open with the default 7
        assertThat(serviceAt("2026-10-11T12:00:00Z", 3).isEventAcceptingUploads(event.getId())).isFalse();
        assertThat(serviceAt("2026-10-11T12:00:00Z", 7).isEventAcceptingUploads(event.getId())).isTrue();
    }

    @Test
    void anUnknownEventAcceptsNothing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(serviceAt("2026-10-06T20:00:00Z", 7).isEventAcceptingUploads(id)).isFalse();
    }

    // ── getPublicVisibleEvents ────────────────────────────────────────────────

    @Test
    void publicEventsAreFilteredAndLabelledByTheLiveStatus() {
        MediaEvent ongoingButStale = MediaEvent.builder().id(UUID.randomUUID()).name("Tonight")
                .date(EVENING).status(EventStatus.UPCOMING).build();
        MediaEvent upcoming = MediaEvent.builder().id(UUID.randomUUID()).name("Next week")
                .date(Instant.parse("2026-10-13T19:00:00Z")).status(EventStatus.UPCOMING).build();
        MediaEvent recentPast = MediaEvent.builder().id(UUID.randomUUID()).name("Last week")
                .date(Instant.parse("2026-10-02T19:00:00Z")).status(EventStatus.ONGOING).build();
        MediaEvent oldPast = MediaEvent.builder().id(UUID.randomUUID()).name("Long ago")
                .date(Instant.parse("2026-09-01T19:00:00Z")).status(EventStatus.PAST).build();
        when(repository.findAll()).thenReturn(List.of(ongoingButStale, upcoming, recentPast, oldPast));

        List<MediaEvent> visible = serviceAt("2026-10-06T21:00:00Z", 7).getPublicVisibleEvents();

        assertThat(visible).extracting(MediaEvent::getName).containsExactly("Tonight", "Last week");
        assertThat(visible).extracting(MediaEvent::getStatus)
                .containsExactly(EventStatus.ONGOING, EventStatus.PAST);
        // the entity itself is not mutated, only the copy that is returned
        assertThat(ongoingButStale.getStatus()).isEqualTo(EventStatus.UPCOMING);
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refreshSavesOnlyTheRowsWhoseStatusChanged() {
        MediaEvent stale = MediaEvent.builder().id(UUID.randomUUID()).name("a")
                .date(EVENING).status(EventStatus.UPCOMING).build();
        MediaEvent current = MediaEvent.builder().id(UUID.randomUUID()).name("b")
                .date(Instant.parse("2026-10-01T19:00:00Z")).status(EventStatus.PAST).build();
        when(repository.findAll()).thenReturn(List.of(stale, current));

        serviceAt("2026-10-06T20:00:00Z", 7).refreshEventStatuses(Instant.parse("2026-10-06T20:00:00Z"));

        assertThat(stale.getStatus()).isEqualTo(EventStatus.ONGOING);
        verify(repository, times(1)).save(any());
        verify(repository).save(stale);
        verify(repository, never()).save(current);
    }

    @Test
    void anEventMovesToPastAtMidnightOfItsDay() {
        MediaEvent event = MediaEvent.builder().id(UUID.randomUUID()).name("a")
                .date(EVENING).status(EventStatus.ONGOING).build();
        when(repository.findAll()).thenReturn(List.of(event));

        serviceAt("2026-10-07T00:05:00Z", 7).refreshEventStatuses();

        assertThat(event.getStatus()).isEqualTo(EventStatus.PAST);
    }
}
