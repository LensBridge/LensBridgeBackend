package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.Location;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BoardContextTest {

    private static Device deviceWithTimezone(String timezone) {
        Location location = Location.builder().timezone(timezone).build();
        DeviceConfig config = DeviceConfig.builder().location(location).build();
        Device device = Device.builder().displayName("d").build();
        device.setConfig(config);
        return device;
    }

    @Test
    void zoneResolvesFromConfigTimezone() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Toronto"));
        BoardContext ctx = BoardContext.builder()
                .device(deviceWithTimezone("America/Toronto"))
                .config(deviceWithTimezone("America/Toronto").getConfig())
                .now(now)
                .build();

        assertThat(ctx.zone()).isEqualTo(ZoneId.of("America/Toronto"));
    }

    @Test
    void zoneFallsBackToSystemDefaultWhenConfigNull() {
        BoardContext ctx = BoardContext.builder()
                .device(Device.builder().displayName("d").build())
                .config(null)
                .now(ZonedDateTime.now())
                .build();

        assertThat(ctx.zone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void zoneFallsBackToSystemDefaultWhenTimezoneInvalid() {
        Location location = Location.builder().timezone("Not/AZone").build();
        DeviceConfig config = DeviceConfig.builder().location(location).build();

        BoardContext ctx = BoardContext.builder()
                .device(Device.builder().displayName("d").build())
                .config(config)
                .now(ZonedDateTime.now())
                .build();

        assertThat(ctx.zone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void currentWeekStartIsPreviousOrSameSundayAtMidnight() {
        // Wednesday 2026-05-13 12:34 UTC
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 13, 12, 34, 0, 0, ZoneOffset.UTC);
        BoardContext ctx = BoardContext.builder().now(now).build();

        ZonedDateTime weekStart = ctx.currentWeekStart().atZone(ZoneOffset.UTC);

        assertThat(weekStart.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(weekStart.toLocalDate()).isEqualTo(now.toLocalDate().minusDays(3));
        assertThat(weekStart.getHour()).isZero();
        assertThat(weekStart.getMinute()).isZero();
    }

    @Test
    void currentWeekEndIsNextOrSameSaturdayEndOfDay() {
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 13, 12, 34, 0, 0, ZoneOffset.UTC);
        BoardContext ctx = BoardContext.builder().now(now).build();

        ZonedDateTime weekEnd = ctx.currentWeekEnd().atZone(ZoneOffset.UTC);

        assertThat(weekEnd.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(weekEnd.toLocalDate()).isEqualTo(now.toLocalDate().plusDays(3));
        assertThat(weekEnd.getHour()).isEqualTo(23);
        assertThat(weekEnd.getMinute()).isEqualTo(59);
    }

    @Test
    void weekStartIsBeforeWeekEnd() {
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 13, 12, 34, 0, 0, ZoneOffset.UTC);
        BoardContext ctx = BoardContext.builder().now(now).build();

        assertThat(ctx.currentWeekStart()).isBefore(ctx.currentWeekEnd());
    }

    @Test
    void currentDayStartAndEndBracketTheMoment() {
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 13, 12, 34, 0, 0, ZoneOffset.UTC);
        BoardContext ctx = BoardContext.builder().now(now).build();

        Instant dayStart = ctx.currentDayStart();
        Instant dayEnd = ctx.currentDayEnd();

        assertThat(dayStart).isBeforeOrEqualTo(now.toInstant());
        assertThat(dayEnd).isAfter(now.toInstant());
        assertThat(dayStart.atZone(ZoneOffset.UTC).toLocalDate())
                .isEqualTo(now.toLocalDate());
    }

    @Test
    void ofBuildsContextFromDeviceConfigTimezone() {
        Device device = deviceWithTimezone("America/Toronto");

        BoardContext ctx = BoardContext.of(device);

        assertThat(ctx.getDevice()).isSameAs(device);
        assertThat(ctx.getConfig()).isSameAs(device.getConfig());
        assertThat(ctx.getNow().getZone()).isEqualTo(ZoneId.of("America/Toronto"));
    }

    @Test
    void ofUsesSystemDefaultWhenConfigMissing() {
        Device device = Device.builder().displayName("d").build();

        BoardContext ctx = BoardContext.of(device);

        assertThat(ctx.getConfig()).isNull();
        assertThat(ctx.getNow().getZone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void ofFallsBackToSystemDefaultWhenTimezoneInvalid() {
        Device device = deviceWithTimezone("Bogus/Zone");

        BoardContext ctx = BoardContext.of(device);

        assertThat(ctx.getNow().getZone()).isEqualTo(ZoneId.systemDefault());
    }
}
