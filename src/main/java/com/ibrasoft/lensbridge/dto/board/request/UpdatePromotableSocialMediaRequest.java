package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.SocialType;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.ibrasoft.lensbridge.model.board.PromotableSocialMedia;
import lombok.*;
import org.hibernate.validator.constraints.URL;

/**
 * Patch request: null leaves a field alone. The one exception is {@link #handle}, where an
 * empty string clears it — a page that drops its handle would otherwise be unfixable without
 * deleting and recreating the entry.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdatePromotableSocialMediaRequest {

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Positive(message = "Duration must be positive")
    private Integer duration;

    private Audience audience;

    private SocialType type;

    @URL(message = "URL must be a valid URL")
    @Size(max = 255, message = "URL must be at most 255 characters")
    private String url;

    @Size(max = PromotableSocialMedia.TEXT_MAX, message = "Header text must be at most 1000 characters")
    private String headerText;

    @Size(max = PromotableSocialMedia.TEXT_MAX, message = "Hero text must be at most 1000 characters")
    private String heroText;

    /** Send an empty string to clear it. */
    @Size(max = 255, message = "Handle must be at most 255 characters")
    private String handle;

    @Size(max = PromotableSocialMedia.TEXT_MAX, message = "Footer text must be at most 1000 characters")
    private String footerText;
}
