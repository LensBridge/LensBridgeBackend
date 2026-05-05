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
public class JummahFrameConfig extends FrameConfig {

    private List<JummahSlot> prayers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JummahSlot {
        /** ISO-8601 LocalTime string, e.g. "13:30" */
        private String prayerTime;
        private String khatib;
        private String location;
    }
}
