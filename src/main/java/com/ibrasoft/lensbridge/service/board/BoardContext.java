package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.model.board.BoardConfig;
import com.ibrasoft.lensbridge.model.board.BoardLocation;
import lombok.Builder;
import lombok.Value;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

@Value
@Builder
public class BoardContext {
    BoardLocation location;
    BoardConfig config;
    ZonedDateTime now;

    public ZoneId zone() {
        // Prefer the board's configured timezone; fall back to system default.
        if (config != null && config.getLocation() != null && config.getLocation().getTimezone() != null) {
            try {
                return ZoneId.of(config.getLocation().getTimezone());
            } catch (Exception ignored) { /* fallthrough */ }
        }
        return ZoneId.systemDefault();
    }

    public Instant currentWeekStart() {
        return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                  .with(LocalTime.MIN)
                  .toInstant();
    }

    public Instant currentWeekEnd() {
        return now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                  .with(LocalTime.MAX)
                  .toInstant();
    }

    public Instant currentDayStart() {
        return now.with(LocalTime.MIN).toInstant();
    }

    public Instant currentDayEnd() {
        return now.with(LocalTime.MAX).toInstant();
    }

    public static BoardContext of(BoardLocation location, BoardConfig config) {
        ZoneId zone = ZoneId.systemDefault();
        if (config != null && config.getLocation() != null && config.getLocation().getTimezone() != null) {
            try { zone = ZoneId.of(config.getLocation().getTimezone()); }
            catch (Exception ignored) { /* keep default */ }
        }
        return BoardContext.builder()
                .location(location)
                .config(config)
                .now(ZonedDateTime.now(zone))
                .build();
    }
}
