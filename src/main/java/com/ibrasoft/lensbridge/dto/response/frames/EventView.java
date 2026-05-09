package com.ibrasoft.lensbridge.dto.response.frames;

import lombok.*;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EventView {
    private String name;
    private String description;
    private String location;
    private Instant startTime;
    private Instant endTime;
    private Boolean allDay;
}
