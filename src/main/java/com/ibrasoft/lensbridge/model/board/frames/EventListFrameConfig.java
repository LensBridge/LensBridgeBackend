package com.ibrasoft.lensbridge.model.board.frames;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventListFrameConfig extends FrameConfig {

    /** e.g. "This Week" */
    private String heading;

    /** Flat presentation records */
    private List<EventView> events;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventView {
        private String name;
        private String description;
        private String location;
        private Long startTimestamp;
        private Long endTimestamp;
        private Boolean allDay;
    }
}
