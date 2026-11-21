package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.SocialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.ibrasoft.lensbridge.model.board.PromotableSocialMedia;
import lombok.*;
import org.hibernate.validator.constraints.URL;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreatePromotableSocialMediaRequest {

    /** Admin-facing label, not shown on the board. */
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name must be at most 255 characters")
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
    @Size(min = 1, max = 255, message = "URL must be at most 255 characters")
    private String url;

    // The text fields below are stored as markdown and rendered to HTML by the frontend.

    @NotBlank(message = "Header text is required")
    @Size(min = 1, max = PromotableSocialMedia.TEXT_MAX, message = "Header text must be at most 1000 characters")
    private String headerText;

    @NotBlank(message = "Hero text is required")
    @Size(min = 1, max = PromotableSocialMedia.TEXT_MAX, message = "Hero text must be at most 1000 characters")
    private String heroText;

    /** Optional — platforms like WhatsApp have no handle. */
    @Size(max = 255, message = "Handle must be at most 255 characters")
    private String handle;

    @NotBlank(message = "Footer text is required")
    @Size(min = 1, max = PromotableSocialMedia.TEXT_MAX, message = "Footer text must be at most 1000 characters")
    private String footerText;
}
