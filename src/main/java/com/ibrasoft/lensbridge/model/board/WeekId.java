package com.ibrasoft.lensbridge.model.board;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeekId {
    private Integer year;
    private Integer weekNumber;

    public static WeekId fromDate(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return new WeekId(year, week);
    }

    public LocalDate getWeekStart() {
        return LocalDate.of(year, 1, 1)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, weekNumber)
            .with(DayOfWeek.MONDAY);
    }

    public LocalDate getWeekEnd() {
        return getWeekStart().plusDays(6);
    }
}
