package com.ibrasoft.lensbridge.model.minbar.board.frames;

import com.ibrasoft.lensbridge.model.minbar.board.PromotableSocialMedia;
import com.ibrasoft.lensbridge.model.minbar.board.SocialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * One promoted social account, as the board renders it: platform, QR destination, and the copy
 * around them. All of it comes from a {@link PromotableSocialMedia}
 * row, so an operator can add or reword an account without a frontend deploy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
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
