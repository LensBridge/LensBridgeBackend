package com.ibrasoft.lensbridge.dto.board.request;

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

    /**
     * Sign-up link rendered as a QR code beside the poster. Send an empty string
     * to clear it; null leaves the existing value alone, like every other field
     * on this patch request.
     */
    private String signupUrl;
}
