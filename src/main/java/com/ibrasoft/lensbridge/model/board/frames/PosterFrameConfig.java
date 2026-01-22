package com.ibrasoft.lensbridge.model.board.frames;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Frame configuration specific to poster frames.
 * Contains the poster image URL to be displayed on the board.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PosterFrameConfig extends FrameConfig {
    
    /**
     * The URL of the poster image to display.
     */
    private String posterUrl;
    
    /**
     * The title of the poster (optional, for accessibility/logging).
     */
    private String title;
}
