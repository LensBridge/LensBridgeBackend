package com.ibrasoft.lensbridge.dto.board.response.frames;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * One day of the agenda window, in the device's own timezone.
 * <p>
 * Buckets are emitted for every day in the window, including days with no events — the board
 * renders an empty day as "nothing scheduled", which it could not distinguish from "day outside
 * the window" if empty buckets were dropped.
 * <p>
 * Deliberately carries no {@code isToday} flag. A board can run for days without a payload
 * refresh, so any "today" marker computed here goes stale at the first midnight. The client
 * compares its own current date against {@link #date} instead.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayBucket {

    /** Local date in the device's timezone, not UTC. */
    private LocalDate date;

    /** Events overlapping this day, earliest first. Never null; empty for a free day. */
    private List<EventView> events;
}
