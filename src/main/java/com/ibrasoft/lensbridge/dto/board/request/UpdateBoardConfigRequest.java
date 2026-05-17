package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Location;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateBoardConfigRequest {
    private Location location;
    @Min(value = 1000, message = "Poster cycle interval must be at least 1000ms")
    private Integer posterCycleIntervalMs;
    @Min(value = 0)
    private Integer refreshAfterIshaMinutes;
    private Boolean darkModeAfterIsha;
    @Min(value = 0)
    private Integer darkModeAfterMaghribMinutes;
    private Boolean enableScrollingMessage;
    private List<String> scrollingMessages;
}
