package com.ibrasoft.lensbridge.model.board.frames;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IslamicQuoteFrameConfig extends FrameConfig {

    private Kind kind;
    private String arabic;
    private String transliteration;
    private String translation;
    private String reference;

    public enum Kind {
        VERSE,
        HADITH
    }
}
