package com.ibrasoft.lensbridge.model.board;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JummahPrayer {
    private String time;
    private String khatib;
    private String location;
    private LocalDate date;
}
