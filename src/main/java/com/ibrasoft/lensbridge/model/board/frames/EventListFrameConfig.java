package com.ibrasoft.lensbridge.model.board.frames;

import com.ibrasoft.lensbridge.dto.board.response.frames.EventView;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventListFrameConfig extends FrameConfig {
    private String heading;
    private List<EventView> events;
}
