package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.Location;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BoardContextTest {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Toronto");

    /** A Wednesday, so week boundaries land either side of it. */
    private static final ZonedDateTime WEDNESDAY =
            ZonedDateTime.of(2026, 5, 13, 12, 34, 0, 0, ZoneOffset.UTC);

    private static Device deviceWithTimezone(String timezone) {
        Location location = Location.builder().timezone(timezone).build();
        DeviceConfig config = DeviceConfig.builder().location(location).build();
        Device device = Device.builder().displayName("d").build();
        device.setConfig(config);
        return device;
    }

    private static BoardContext at(ZonedDateTime now) {
        return BoardContext.builder().now(now).build();
    }

    @Test
    void zoneIsTheZoneOfNow() {
        assertThat(at(ZonedDateTime.now(DEFAULT_ZONE)).zone()).isEqualTo(DEFAULT_ZONE);
    }

    @Test
    void ofResolvesZoneFromConfigTimezone() {
        Device device = deviceWithTimezone("Asia/Dubai");

        BoardContext ctx = BoardContext.of(device, DEFAULT_ZONE);

        assertThat(ctx.getDevice()).isSameAs(device);
        assertThat(ctx.getConfig()).isSameAs(device.getConfig());
        assertThat(ctx.zone()).isEqualTo(ZoneId.of("Asia/Dubai"));
    }

    @Test
    void ofFallsBackToSuppliedDefaultWhenConfigMissing() {
        BoardContext ctx = BoardContext.of(Device.builder().displayName("d").build(), DEFAULT_ZONE);

        assertThat(ctx.getConfig()).isNull();
        assertThat(ctx.zone()).isEqualTo(DEFAULT_ZONE);
    }

    @Test
    void ofFallsBackToSuppliedDefaultWhenTimezoneInvalid() {
        assertThat(BoardContext.of(deviceWithTimezone("Bogus/Zone"), DEFAULT_ZONE).zone())
                .isEqualTo(DEFAULT_ZONE);
    }

    @Test
    void ofUsesTheSuppliedDefaultRatherThanTheJvmZone() {
        ZoneId elsewhere = ZoneId.of("Asia/Tokyo");

        assertThat(BoardContext.of(deviceWithTimezone(null), elsewhere).zone()).isEqualTo(elsewhere);
    }

    @Test
    void currentWeekStartIsPreviousOrSameMondayAtMidnight() {
        ZonedDateTime weekStart = at(WEDNESDAY).currentWeekStart().atZone(ZoneOffset.UTC);

        assertThat(weekStart.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(weekStart.toLocalDate()).isEqualTo(WEDNESDAY.toLocalDate().minusDays(2));
        assertThat(weekStart.getHour()).isZero();
        assertThat(weekStart.getMinute()).isZero();
    }

    @Test
    void currentWeekEndIsNextOrSameSundayEndOfDay() {
        ZonedDateTime weekEnd = at(WEDNESDAY).currentWeekEnd().atZone(ZoneOffset.UTC);

        assertThat(weekEnd.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(weekEnd.toLocalDate()).isEqualTo(WEDNESDAY.toLocalDate().plusDays(4));
        assertThat(weekEnd.getHour()).isEqualTo(23);
        assertThat(weekEnd.getMinute()).isEqualTo(59);
    }

    /**
     * The bug the ISO convention fixes: under Sunday-start weeks a board on Sunday looked
     * ahead to next week's events while still showing the outgoing week's jummah times.
     */
    @Test
    void sundayBelongsToTheWeekThatIsEnding() {
        ZonedDateTime sunday = WEDNESDAY.plusDays(4);
        BoardContext ctx = at(sunday);

        assertThat(ctx.currentWeekStart().atZone(ZoneOffset.UTC).toLocalDate())
                .isEqualTo(WEDNESDAY.toLocalDate().minusDays(2));
        assertThat(ctx.currentWeekEnd()).isAfter(sunday.toInstant());
    }

    @Test
    void weekBracketsTheMoment() {
        BoardContext ctx = at(WEDNESDAY);

        assertThat(ctx.currentWeekStart()).isBefore(WEDNESDAY.toInstant());
        assertThat(ctx.currentWeekEnd()).isAfter(WEDNESDAY.toInstant());
    }

    @Test
    void currentDayStartAndEndBracketTheMoment() {
        BoardContext ctx = at(WEDNESDAY);

        Instant dayStart = ctx.currentDayStart();
        Instant dayEnd = ctx.currentDayEnd();

        assertThat(dayStart).isBeforeOrEqualTo(WEDNESDAY.toInstant());
        assertThat(dayEnd).isAfter(WEDNESDAY.toInstant());
        assertThat(dayStart.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(WEDNESDAY.toLocalDate());
    }

    @Test
    void todayIsTheLocalDateInTheDeviceZone() {
        // 00:30 UTC on the 14th is still the 13th in Toronto.
        ZonedDateTime justAfterMidnightUtc = ZonedDateTime.of(2026, 5, 14, 0, 30, 0, 0, ZoneOffset.UTC);

        BoardContext ctx = at(justAfterMidnightUtc.withZoneSameInstant(DEFAULT_ZONE));

        assertThat(ctx.today()).isEqualTo(LocalDate.of(2026, 5, 13));
    }
}
