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
public class DailyScheduleFrameConfig extends FrameConfig {

    private String heading;

    private List<EventListFrameConfig.EventView> events;
}
