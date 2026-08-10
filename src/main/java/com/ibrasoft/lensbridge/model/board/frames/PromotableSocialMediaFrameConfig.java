package com.ibrasoft.lensbridge.model.board.frames;

import com.ibrasoft.lensbridge.model.board.SocialType;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Builder
@Data
public class PromotableSocialMediaFrameConfig extends FrameConfig {
    private SocialType socialType;

    private String url;

    /**
     * Small 2-3 word tagline (i.e: follow along)
     */
    private String headerText;

    /**
     * Main text shown to the user
     */
    private String heroText;

    /**
     * Social media handle, if applicable (i.e: WhatsApp doesn't have a handle)
     */
    private String handle;

    /**
     * Descriptive block of text
     * i.e: Follow your home on campus for event recaps, announcements, and more!
     */
    private String footerText;
}
