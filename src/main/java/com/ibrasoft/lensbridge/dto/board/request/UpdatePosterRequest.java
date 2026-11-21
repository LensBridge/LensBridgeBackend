package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdatePosterRequest {
    @Size(max = 255, message = "Title must be at most 255 characters")
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
    @Size(max = 255, message = "Signup URL must be at most 255 characters")
    private String signupUrl;
}
