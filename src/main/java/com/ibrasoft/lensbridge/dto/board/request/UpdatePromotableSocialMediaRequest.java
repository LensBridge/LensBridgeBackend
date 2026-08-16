package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.board.SocialType;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.validator.constraints.URL;

/**
 * Patch request: null leaves a field alone. The one exception is {@link #handle}, where an
 * empty string clears it — a page that drops its handle would otherwise be unfixable without
 * deleting and recreating the entry.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdatePromotableSocialMediaRequest {

    private String name;

    @Positive(message = "Duration must be positive")
    private Integer duration;

    private Audience audience;

    private SocialType type;

    @URL(message = "URL must be a valid URL")
    private String url;

    private String headerText;

    private String heroText;

    /** Send an empty string to clear it. */
    private String handle;

    private String footerText;
}
