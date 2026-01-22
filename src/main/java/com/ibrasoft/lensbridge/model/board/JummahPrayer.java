package com.ibrasoft.lensbridge.model.board;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JummahPrayer {
    private LocalTime prayerTime;
    private String khatib;
    private String location;
}
