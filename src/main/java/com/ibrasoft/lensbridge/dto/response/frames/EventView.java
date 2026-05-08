package com.ibrasoft.lensbridge.dto.response.frames;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventView {
    private String name;
    private String description;
    private String location;
    private Long startEpochMs;
    private Long endEpochMs;
    private Boolean allDay;
}
