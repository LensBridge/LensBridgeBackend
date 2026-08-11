package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Location;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateBoardConfigRequest {
    private Location location;
    private Boolean darkModeAfterIsha;
    private Boolean enableScrollingMessage;
    private List<String> scrollingMessages;

    /**
     * Seconds to pin the agenda slide for, or {@code 0} to hand the decision back to the board
     * ("auto").
     *
     * Zero rather than null carries that meaning because this endpoint patches by skipping
     * nulls — null already means "leave it alone", so it cannot also mean "clear it".
     *
     * The 1–4 gap is rejected in {@code BoardService}: bean validation cannot express
     * "zero or at least five" without a custom constraint that would leak a bogus property
     * into the generated schema.
     */
    @Min(value = 0, message = "agendaDurationSeconds must be 0 (auto) or between 5 and 120 seconds")
    @Max(value = DeviceConfig.MAX_SLIDE_SECONDS,
         message = "agendaDurationSeconds must be 0 (auto) or between 5 and 120 seconds")
    private Integer agendaDurationSeconds;

    /** Seconds to pin the next-prayer countdown for. No auto mode — the slide's content is fixed. */
    @Min(value = DeviceConfig.MIN_SLIDE_SECONDS,
         message = "nextPrayerDurationSeconds must be between 5 and 120 seconds")
    @Max(value = DeviceConfig.MAX_SLIDE_SECONDS,
         message = "nextPrayerDurationSeconds must be between 5 and 120 seconds")
    private Integer nextPrayerDurationSeconds;
}
