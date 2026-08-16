package com.ibrasoft.lensbridge.model.minbar.board.frames;

import com.ibrasoft.lensbridge.dto.board.response.frames.DayBucket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The board's rolling agenda: every day from today through the end of the window, bucketed in
 * the device's timezone.
 * <p>
 * This replaced the former {@code event_list} and {@code daily_schedule} frames, which queried
 * the same events over a 7-day and a 1-day window respectively and differed only in layout.
 * Today's events are simply the first bucket.
 * <p>
 * The backend fixes the <em>content</em> of the window; the board advances through it as the day
 * passes. Which day is current, which event is next, and how far to scroll all change by the
 * minute, far faster than a payload refresh, so they are necessarily client-side. Every time here
 * is an absolute {@code Instant} for that reason — a pre-formatted "in 2 hours" would be wrong
 * within two hours.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgendaFrameConfig extends FrameConfig {

    private String heading;

    /** One entry per day in the window, chronological, including days with no events. */
    private List<DayBucket> days;
}
