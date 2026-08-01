package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Location;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateBoardConfigRequest {
    private Location location;
    private Boolean darkModeAfterIsha;
    private Boolean enableScrollingMessage;
    private List<String> scrollingMessages;
}
