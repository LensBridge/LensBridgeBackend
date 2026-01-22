package com.ibrasoft.lensbridge.model.board;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Objects;

public class WeekId {
    private Integer year;
    private Integer weekNumber;
    
    public WeekId() {}
    
    public WeekId(Integer year, Integer weekNumber) {
        this.year = year;
        this.weekNumber = weekNumber;
    }
    
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeekId weekId = (WeekId) o;
        return Objects.equals(year, weekId.year) && 
               Objects.equals(weekNumber, weekId.weekNumber);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(year, weekNumber);
    }
    
    // Getters and setters
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }
}