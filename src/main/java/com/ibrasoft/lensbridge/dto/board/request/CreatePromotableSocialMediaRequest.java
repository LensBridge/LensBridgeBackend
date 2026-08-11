package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.SocialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.validator.constraints.URL;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreatePromotableSocialMediaRequest {

    /** Admin-facing label, not shown on the board. */
    @NotBlank(message = "Name is required")
    private String name;

    @Positive(message = "Duration must be positive")
    private int duration;

    @NotNull(message = "Audience is required")
    private Audience audience;

    @NotNull(message = "Social type is required")
    private SocialType type;

    /** QR destination. */
    @NotBlank(message = "URL is required")
    @URL(message = "URL must be a valid URL")
    private String url;

    // The text fields below are stored as markdown and rendered to HTML by the frontend.

    @NotBlank(message = "Header text is required")
    private String headerText;

    @NotBlank(message = "Hero text is required")
    private String heroText;

    /** Optional — platforms like WhatsApp have no handle. */
    private String handle;

    @NotBlank(message = "Footer text is required")
    private String footerText;
}
