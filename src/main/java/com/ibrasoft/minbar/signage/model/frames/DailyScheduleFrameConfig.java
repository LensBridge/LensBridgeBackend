package com.ibrasoft.minbar.signage.model.frames;

import com.ibrasoft.minbar.signage.dto.response.frames.EventView;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DailyScheduleFrameConfig extends FrameConfig {
    private String heading;
    private List<EventView> events;
}
