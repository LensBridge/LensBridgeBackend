package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.dto.board.response.frames.DayBucket;
import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.frames.AgendaFrameConfig;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Emits the single AGENDA frame: every day from today to the end of the rolling window, each
 * carrying the events that fall on it in the device's timezone.
 * <p>
 * This replaced the {@code event_list} and {@code daily_schedule} producers. Both ran the same
 * query over different windows and differed only in how the frontend drew the result, so they
 * were one frame with two layouts rather than two frames. Today's events are the first bucket.
 */
@Component
@RequiredArgsConstructor
@Order(2)
public class AgendaFrameProducer implements FrameProducer {

    private final BoardService boardService;

    @Override
    public List<FrameDefinition> produce(BoardContext ctx) {
        List<BoardEvent> events = boardService.getEventsForAudienceInRange(
                ctx.getDevice().getAudience(), ctx.currentDayStart(), ctx.rollingWindowEnd());

        // An agenda reading "nothing scheduled" for seven straight days is worse than no agenda
        // at all. Both frames this replaced skipped themselves when empty; so does this one.
        if (events.isEmpty()) return List.of();

        return List.of(transform(events, ctx));
    }

    public FrameDefinition transform(List<BoardEvent> events, BoardContext ctx) {
        AgendaFrameConfig config = AgendaFrameConfig.builder()
                .heading("Next 7 Days")
                .days(bucketByDay(events, ctx))
                .build();

        return FrameDefinition.builder()
                .frameId("agenda")
                .frameType(FrameType.AGENDA)
                .durationInSeconds(configuredDuration(ctx))
                .frameConfig(config)
                .build();
    }

    /**
     * Null means "auto": the board grows the dwell time with the number of events it ends up
     * rendering. Only the board can decide that — it knows which day is current and how many
     * rows survived the queue limit, and both change while a single payload is on screen.
     */
    private Integer configuredDuration(BoardContext ctx) {
        return ctx == null || ctx.getConfig() == null
                ? null
                : ctx.getConfig().getAgendaDurationSeconds();
    }

    /**
     * Buckets by local date, emitting every day in the window whether or not it has events.
     * The last day is derived from {@link BoardContext#rollingWindowEnd()} rather than hardcoded,
     * so widening the window here needs no change.
     * <p>
     * A multi-day event appears in every bucket it overlaps, not only the one it starts in — a
     * conference running Monday to Wednesday should show up on Tuesday's agenda.
     */
    private List<DayBucket> bucketByDay(List<BoardEvent> events, BoardContext ctx) {
        LocalDate firstDay = ctx.today();
        LocalDate lastDay = ctx.rollingWindowEnd().atZone(ctx.zone()).toLocalDate();

        List<DayBucket> buckets = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            Instant dayStart = day.atStartOfDay(ctx.zone()).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(ctx.zone()).toInstant();

            List<EventView> onThisDay = events.stream()
                    .filter(e -> overlaps(e, dayStart, dayEnd))
                    .sorted(Comparator.comparing(BoardEvent::getStartTime))
                    .map(EventView::of)
                    .toList();

            buckets.add(DayBucket.builder().date(day).events(onThisDay).build());
        }
        return buckets;
    }

    /**
     * Half-open overlap: an event occupies {@code [start, end)} and the day is
     * {@code [dayStart, dayEnd)}, so an event ending exactly at midnight belongs to the day it
     * ended, not the next one.
     * <p>
     * A zero-length event would overlap nothing under that rule, so it is treated as an instant
     * on its start day. Same for the malformed {@code end < start} case.
     */
    private boolean overlaps(BoardEvent event, Instant dayStart, Instant dayEnd) {
        Instant start = event.getStartTime();
        Instant end = event.getEndTime().isAfter(start) ? event.getEndTime() : start.plusNanos(1);
        return start.isBefore(dayEnd) && end.isAfter(dayStart);
    }
}
