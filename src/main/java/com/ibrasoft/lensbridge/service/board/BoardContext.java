package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.model.board.Device;
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
    Device device;
    DeviceConfig config;
    ZonedDateTime now;

    public ZoneId zone() {
        if (config != null && config.getLocation() != null && config.getLocation().getTimezone() != null) {
            try {
                return ZoneId.of(config.getLocation().getTimezone());
            } catch (Exception ignored) {}
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

    public static BoardContext of(Device device) {
        DeviceConfig config = device.getConfig();
        ZoneId zone = ZoneId.systemDefault();
        if (config != null && config.getLocation() != null && config.getLocation().getTimezone() != null) {
            try { zone = ZoneId.of(config.getLocation().getTimezone()); }
            catch (Exception ignored) {}
        }
        return BoardContext.builder()
                .device(device)
                .config(config)
                .now(ZonedDateTime.now(zone))
                .build();
    }
}
