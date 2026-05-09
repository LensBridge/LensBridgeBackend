package com.ibrasoft.lensbridge.dto.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdatePosterRequest {
    private String title;

    @Positive(message = "Duration must be positive")
    private Integer duration;

    private Instant startTime;
    private Instant endTime;
    private Audience audience;
}
