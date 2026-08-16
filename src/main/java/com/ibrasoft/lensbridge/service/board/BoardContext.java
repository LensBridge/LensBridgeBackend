package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.model.minbar.board.WeekId;
import com.ibrasoft.lensbridge.model.minbar.board.embedded.DeviceConfig;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Everything a payload assembly needs to know about "when" and "where" for one board.
 * <p>
 * All boundaries are computed in the device's own timezone, never the server's. A board in
 * Toronto and a backend container running UTC must agree on which events are "today".
 * <p>
 * Weeks run <b>Monday to Sunday</b>, matching {@link WeekId},
 * which keys the {@code weekly_content} table. The two used to disagree — events were
 * bucketed Sunday–Saturday — so on Sundays a board showed next week's events next to last
 * week's jummah times.
 */
@Value
@Builder
@Slf4j
public class BoardContext {

    Device device;
    DeviceConfig config;
    ZonedDateTime now;

    /** The device's timezone, already resolved into {@link #now}. */
    public ZoneId zone() {
        return now.getZone();
    }

    /** Local date in the device's timezone — the key for "which week's content". */
    public LocalDate today() {
        return now.toLocalDate();
    }

    public Instant currentWeekStart() {
        return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                  .with(LocalTime.MIN)
                  .toInstant();
    }

    public Instant currentWeekEnd() {
        return now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                  .with(LocalTime.MAX)
                  .toInstant();
    }

    public Instant rollingWindowEnd() {
        return now.plusDays(6).with(LocalTime.MAX).toInstant();
    }

    public Instant currentDayStart() {
        return now.with(LocalTime.MIN).toInstant();
    }

    public Instant currentDayEnd() {
        return now.with(LocalTime.MAX).toInstant();
    }

    /**
     * @param defaultZone used when the device has no timezone configured or the configured
     *                    one is unparseable. Pass the deployment's default, not
     *                    {@code ZoneId.systemDefault()} — the JVM's zone is an accident of
     *                    the container image.
     */
    public static BoardContext of(Device device, ZoneId defaultZone) {
        DeviceConfig config = device.getConfig();
        ZoneId zone = resolveZone(device, config, defaultZone);
        return BoardContext.builder()
                .device(device)
                .config(config)
                .now(ZonedDateTime.now(zone))
                .build();
    }

    private static ZoneId resolveZone(Device device, DeviceConfig config, ZoneId defaultZone) {
        String configured = config == null || config.getLocation() == null
                ? null
                : config.getLocation().getTimezone();
        if (configured == null || configured.isBlank()) return defaultZone;

        try {
            return ZoneId.of(configured);
        } catch (DateTimeException e) {
            log.warn("Device {} has an unparseable timezone '{}' — falling back to {}",
                    device.getId(), configured, defaultZone);
            return defaultZone;
        }
    }
}
