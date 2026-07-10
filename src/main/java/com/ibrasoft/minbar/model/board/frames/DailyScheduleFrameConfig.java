package com.ibrasoft.minbar.model.board.frames;

import com.ibrasoft.minbar.dto.board.response.frames.EventView;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DailyScheduleFrameConfig extends FrameConfig {
    private String heading;
    private List<EventView> events;
}
