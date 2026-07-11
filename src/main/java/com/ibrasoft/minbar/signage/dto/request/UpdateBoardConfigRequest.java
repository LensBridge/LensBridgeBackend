package com.ibrasoft.minbar.signage.dto.request;

import com.ibrasoft.minbar.signage.model.Location;
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
